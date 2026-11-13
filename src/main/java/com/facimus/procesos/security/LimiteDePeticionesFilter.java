package com.facimus.procesos.security;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

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
            "/api/v1/auth/identidades/vinculo", "/api/v1/auth/passkeys/opciones", "/api/v1/auth/passkeys/entrada",
            "/api/v1/auth/segundo-factor/verificacion/passkey", "/api/v1/auth/reautenticacion/passkey");

    private static final String REGISTRO = "/api/v1/empresas";

    /** PR 65: la unica lectura que gasta de un cubo, el de buscar de cada persona. */
    private static final String BUSQUEDA = "/api/v1/busqueda";

    /** PR 54: las rutas que reciben un archivo BPMN, que tienen su propio tope, el mismo que lee SecureXml. */
    private static final Pattern RUTAS_BPMN = Pattern
            .compile("/api/v1/procesos/(importar|\\d+/(modelo|comparar|versiones/\\d+/dibujo))");

    /** PR 61: las rutas que reciben un documento, con el tope de un documento. */
    private static final Pattern RUTAS_DOCUMENTOS = Pattern
            .compile("/api/v1/(procesos/\\d+/documentos|documentos/\\d+/versiones)");

    private static final Set<String> CORREO = Set.of("/api/v1/auth/recuperacion", "/api/v1/auth/verificacion",
            "/api/v1/usuarios/invitaciones");

    private final LimitesDePeticiones limites;
    private final VerificacionHumana verificacionHumana;
    private final JsonMapper jsonMapper;
    private final long cuerpoMaximo;
    private final long cuerpoMaximoBpmn;
    private final long cuerpoMaximoDocumento;

    public LimiteDePeticionesFilter(LimitesDePeticiones limites, VerificacionHumana verificacionHumana,
            JsonMapper jsonMapper, long cuerpoMaximo, long cuerpoMaximoBpmn, long cuerpoMaximoDocumento) {
        this.limites = limites;
        this.verificacionHumana = verificacionHumana;
        this.jsonMapper = jsonMapper;
        this.cuerpoMaximo = cuerpoMaximo;
        this.cuerpoMaximoBpmn = cuerpoMaximoBpmn;
        this.cuerpoMaximoDocumento = cuerpoMaximoDocumento;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String ruta = ruta(request);
        return !ruta.startsWith("/api/") || SIN_EFECTOS.contains(request.getMethod()) && !esBusqueda(request, ruta);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String ruta = ruta(request);
        long tope = tope(ruta);
        if (request.getContentLengthLong() > tope) {
            responder(request, response, HttpStatus.CONTENT_TOO_LARGE, "Cuerpo demasiado grande",
                    "La petición no puede superar " + (tope / 1024) + " KB.", null);
            return;
        }
        String ip = request.getRemoteAddr();
        Optional<Duration> espera = clase(request, ruta).flatMap(clase -> limites.gastar(clase,
                porPersona(clase) ? "usuario:" + usuario().orElseThrow() : "ip:" + ip));
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

    /**
     * La ruta como la compara Spring MVC al elegir el controller: decodificada y sin parametros de ruta. Comparar la
     * URI tal como llega dejaria pasar {@code /api/v1/busqued%61} o {@code /api/v1/auth/logi%6E} sin gastar de su cubo,
     * y llegarian al mismo controller.
     */
    private static String ruta(HttpServletRequest request) {
        return UrlPathHelper.defaultInstance.getPathWithinApplication(request);
    }

    private long tope(String ruta) {
        if (RUTAS_BPMN.matcher(ruta).matches()) {
            return cuerpoMaximoBpmn;
        }
        return RUTAS_DOCUMENTOS.matcher(ruta).matches() ? cuerpoMaximoDocumento : cuerpoMaximo;
    }

    private static boolean esBusqueda(HttpServletRequest request, String ruta) {
        return "GET".equals(request.getMethod()) && BUSQUEDA.equals(ruta);
    }

    private static boolean porPersona(ClaseDeLimite clase) {
        return clase == ClaseDeLimite.ESCRITURA || clase == ClaseDeLimite.BUSQUEDA;
    }

    /** De que cubo gasta. Lo que cambia algo sin sesion no cuenta por persona: la autorizacion lo va a rechazar. */
    private static Optional<ClaseDeLimite> clase(HttpServletRequest request, String ruta) {
        if (esBusqueda(request, ruta)) {
            return usuario().map(id -> ClaseDeLimite.BUSQUEDA);
        }
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
