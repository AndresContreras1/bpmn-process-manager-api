package com.facimus.procesos.security;

import java.io.IOException;
import java.net.URI;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.facimus.procesos.common.security.ApiPrincipal;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

/**
 * D17: quien entro con una contrasena temporal no puede hacer nada mas que cambiarla. Va detras del filtro del JWT,
 * asi que trabaja sobre la identidad que trae el token y no consulta la base.
 */
public class CambioDeClaveFilter extends OncePerRequestFilter {

    static final String MOTIVO = "Debe cambiar su contraseña antes de seguir.";

    /** Cambiar la clave, cerrar la sesion y renovar el token: lo minimo para poder cambiarla y salir. */
    private static final Set<String> PERMITIDAS = Set.of("/api/v1/auth/password", "/api/v1/auth/logout",
            "/api/v1/auth/refresh");

    private final JsonMapper jsonMapper;

    public CambioDeClaveFilter(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (tieneQueCambiarla() && !PERMITIDAS.contains(request.getRequestURI())) {
            responder(request, response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private static boolean tieneQueCambiarla() {
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
        return autenticacion != null && autenticacion.getPrincipal() instanceof ApiPrincipal principal
                && principal.debeCambiarClave();
    }

    private void responder(HttpServletRequest request, HttpServletResponse response) throws IOException {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, MOTIVO);
        problema.setTitle("Sin permisos");
        problema.setInstance(URI.create(request.getRequestURI()));

        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        jsonMapper.writeValue(response.getOutputStream(), problema);
    }
}
