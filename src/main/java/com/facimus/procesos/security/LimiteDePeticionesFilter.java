package com.facimus.procesos.security;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.facimus.procesos.common.api.Problemas;
import com.facimus.procesos.common.security.ApiPrincipal;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

/**
 * PR 32: lo que se le pide a la API pasa por su cubo antes de llegar a nada. Un cuerpo de mas del maximo responde 413
 * sin leerse; una peticion sin fichas en su cubo, 429 con Retry-After; un registro sin la verificacion de Turnstile,
 * cuando la instalacion la pide, 400. Va detras de identificar quien es, porque lo que cambia algo cuenta por persona.
 */
public class LimiteDePeticionesFilter extends OncePerRequestFilter {

    private static final Set<String> SIN_EFECTOS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    private static final Set<String> AUTENTICACION = Set.of("/api/v1/auth/login", "/api/v1/auth/refresh",
            "/api/v1/auth/reautenticacion", "/api/v1/auth/segundo-factor/verificacion",
            "/api/v1/auth/identidades/vinculo");

    private static final String REGISTRO = "/api/v1/empresas";

    private static final Set<String> CORREO = Set.of("/api/v1/auth/recuperacion", "/api/v1/auth/verificacion",
            "/api/v1/usuarios/invitaciones");

    private final LimitesDePeticiones limites;
    private final VerificacionHumana verificacionHumana;
    private final JsonMapper jsonMapper;
    private final long cuerpoMaximo;

    public LimiteDePeticionesFilter(LimitesDePeticiones limites, VerificacionHumana verificacionHumana,
            JsonMapper jsonMapper, long cuerpoMaximo) {
        this.limites = limites;
        this.verificacionHumana = verificacionHumana;
        this.jsonMapper = jsonMapper;
        this.cuerpoMaximo = cuerpoMaximo;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return SIN_EFECTOS.contains(request.getMethod()) || !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (request.getContentLengthLong() > cuerpoMaximo) {
            responder(request, response, HttpStatus.CONTENT_TOO_LARGE, "Cuerpo demasiado grande",
                    "La petición no puede superar " + (cuerpoMaximo / 1024) + " KB.", null);
            return;
        }
        String ruta = request.getRequestURI();
        String ip = request.getRemoteAddr();
        Optional<Duration> espera = clase(request, ruta).flatMap(clase -> limites.gastar(clase,
                clase == ClaseDeLimite.ESCRITURA ? "usuario:" + usuario().orElseThrow() : "ip:" + ip));
        if (espera.isPresent()) {
            long segundos = Math.max(1, (espera.get().toMillis() + 999) / 1000);
            responder(request, response, HttpStatus.TOO_MANY_REQUESTS, "Demasiadas peticiones",
                    "Se pidieron demasiadas cosas en poco tiempo. Intenta de nuevo en " + segundos + " s.", segundos);
            return;
        }
        if (REGISTRO.equals(ruta) && !verificacionHumana.esHumano(request.getHeader(VerificacionHumana.CABECERA), ip)) {
            responder(request, response, HttpStatus.BAD_REQUEST, "Verificación requerida",
                    "Falta comprobar que eres una persona: completa la verificación del formulario.", null);
            return;
        }
        filterChain.doFilter(request, response);
    }

    /** De que cubo gasta. Lo que cambia algo sin sesion no cuenta por persona: la autorizacion lo va a rechazar. */
    private static Optional<ClaseDeLimite> clase(HttpServletRequest request, String ruta) {
        boolean esPost = "POST".equals(request.getMethod());
        if (esPost && AUTENTICACION.contains(ruta)) {
            return Optional.of(ClaseDeLimite.AUTENTICACION);
        }
        if (esPost && REGISTRO.equals(ruta)) {
            return Optional.of(ClaseDeLimite.REGISTRO);
        }
        if (esPost && CORREO.contains(ruta)) {
            return Optional.of(ClaseDeLimite.CORREO);
        }
        if (ruta.startsWith("/api/v1/auth/oidc")) {
            return Optional.empty();
        }
        return usuario().map(id -> ClaseDeLimite.ESCRITURA);
    }

    private static Optional<Long> usuario() {
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
        return autenticacion != null && autenticacion.getPrincipal() instanceof ApiPrincipal principal
                ? Optional.of(principal.usuarioId()) : Optional.empty();
    }

    private void responder(HttpServletRequest request, HttpServletResponse response, HttpStatus estado, String titulo,
            String detalle, Long reintentarEn) throws IOException {
        ProblemDetail problema = Problemas.de(estado, titulo, detalle);
        problema.setInstance(URI.create(request.getRequestURI()));
        response.setStatus(estado.value());
        if (reintentarEn != null) {
            response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(reintentarEn));
        }
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        jsonMapper.writeValue(response.getOutputStream(), problema);
    }
}
