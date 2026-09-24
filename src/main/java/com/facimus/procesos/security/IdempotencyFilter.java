package com.facimus.procesos.security;

import java.io.ByteArrayInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import com.facimus.procesos.common.Huella;
import com.facimus.procesos.gestion.dto.response.ReservaIdempotencia;
import com.facimus.procesos.gestion.model.ClaveIdempotencia;
import com.facimus.procesos.gestion.service.IdempotenciaService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

/**
 * Idempotency-Key en los POST autenticados: reintentar con la misma clave devuelve la respuesta de la primera vez en
 * vez de crear otro recurso. La misma clave con otra peticion responde 422, y mientras la primera sigue en curso,
 * 409. Solo se guardan las respuestas 2xx: tras un error, el cliente puede corregir y reintentar con la misma clave.
 */
public class IdempotencyFilter extends OncePerRequestFilter {

    public static final String CABECERA = "Idempotency-Key";
    /** Marca las respuestas que se devuelven guardadas, sin volver a ejecutar la peticion. */
    public static final String REPETIDA = "Idempotent-Replayed";

    private static final int CLAVE_MAXIMA = 100;

    private final IdempotenciaService idempotenciaService;
    private final JsonMapper jsonMapper;

    public IdempotencyFilter(IdempotenciaService idempotenciaService, JsonMapper jsonMapper) {
        this.idempotenciaService = idempotenciaService;
        this.jsonMapper = jsonMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !HttpMethod.POST.matches(request.getMethod()) || request.getHeader(CABECERA) == null
                || principal() == null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String clave = request.getHeader(CABECERA);
        if (clave.isBlank() || clave.length() > CLAVE_MAXIMA) {
            problema(response, request, HttpStatus.BAD_REQUEST, "Idempotency-Key inválida",
                    "La cabecera Idempotency-Key debe tener entre 1 y " + CLAVE_MAXIMA + " caracteres.");
            return;
        }
        ApiPrincipal principal = principal();
        CuerpoRepetible peticion = new CuerpoRepetible(request);
        ReservaIdempotencia reserva = idempotenciaService.reservar(principal.empresaId(), principal.usuarioId(), clave,
                huella(request, peticion.cuerpo));
        switch (reserva) {
            case ReservaIdempotencia.Repetida repetida -> repetir(response, repetida);
            case ReservaIdempotencia.EnCurso enCurso -> problema(response, request, HttpStatus.CONFLICT,
                    "Petición en curso",
                    "Otra petición con esta Idempotency-Key sigue en curso. Reintenta en un momento.");
            case ReservaIdempotencia.Distinta distinta -> problema(response, request, HttpStatus.UNPROCESSABLE_CONTENT,
                    "Idempotency-Key reutilizada", "Esta Idempotency-Key ya se usó con otra petición.");
            case ReservaIdempotencia.Nueva nueva -> ejecutar(peticion, response, filterChain, principal, nueva.id());
        }
    }

    private void ejecutar(CuerpoRepetible peticion, HttpServletResponse response, FilterChain filterChain,
            ApiPrincipal principal, Long reservaId) throws ServletException, IOException {
        ContentCachingResponseWrapper respuesta = new ContentCachingResponseWrapper(response);
        boolean guardada = false;
        try {
            filterChain.doFilter(peticion, respuesta);
            String cuerpo = new String(respuesta.getContentAsByteArray(), StandardCharsets.UTF_8);
            if (HttpStatus.valueOf(respuesta.getStatus()).is2xxSuccessful()
                    && cuerpo.length() <= ClaveIdempotencia.CUERPO_MAXIMO) {
                idempotenciaService.guardar(principal.empresaId(), reservaId, respuesta.getStatus(), cuerpo,
                        respuesta.getHeader(HttpHeaders.LOCATION));
                guardada = true;
            }
        } finally {
            if (!guardada) {
                idempotenciaService.liberar(principal.empresaId(), reservaId);
            }
            respuesta.copyBodyToResponse();
        }
    }

    private static void repetir(HttpServletResponse response, ReservaIdempotencia.Repetida repetida)
            throws IOException {
        response.setStatus(repetida.estado());
        response.setHeader(REPETIDA, "true");
        if (repetida.ubicacion() != null) {
            response.setHeader(HttpHeaders.LOCATION, repetida.ubicacion());
        }
        if (!repetida.cuerpo().isEmpty()) {
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write(repetida.cuerpo());
        }
    }

    private void problema(HttpServletResponse response, HttpServletRequest request, HttpStatus estado, String titulo,
            String detalle) throws IOException {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(estado, detalle);
        problema.setTitle(titulo);
        problema.setInstance(URI.create(request.getRequestURI()));
        response.setStatus(estado.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        jsonMapper.writeValue(response.getOutputStream(), problema);
    }

    private static ApiPrincipal principal() {
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
        return autenticacion != null && autenticacion.getPrincipal() instanceof ApiPrincipal principal ? principal
                : null;
    }

    /** SHA-256 del metodo, la ruta y el cuerpo: la misma clave solo sirve para repetir la misma peticion. */
    private static String huella(HttpServletRequest request, byte[] cuerpo) {
        String peticion = request.getMethod() + " " + request.getRequestURI() + "\n";
        return Huella.de(peticion.getBytes(StandardCharsets.UTF_8), cuerpo);
    }

    /** Lee el cuerpo completo una vez, para calcular la huella, y se lo entrega igual al controller. */
    private static final class CuerpoRepetible extends HttpServletRequestWrapper {

        private final byte[] cuerpo;

        CuerpoRepetible(HttpServletRequest request) throws IOException {
            super(request);
            this.cuerpo = request.getInputStream().readAllBytes();
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream bytes = new ByteArrayInputStream(cuerpo);
            return new ServletInputStream() {
                @Override
                public int read() {
                    return bytes.read();
                }

                @Override
                public boolean isFinished() {
                    return bytes.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener listener) {
                    throw new UnsupportedOperationException("Lectura asincrona no soportada.");
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }
}
