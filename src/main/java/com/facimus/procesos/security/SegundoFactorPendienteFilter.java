package com.facimus.procesos.security;

import java.io.IOException;
import java.net.URI;
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
 * PR 49: quien dio la contrasena y tiene segundo factor no entra todavia. Su token solo sirve para dar el codigo, para
 * salir y para pedir el token CSRF: ninguna peticion de negocio llega a un controller. Va detras del filtro del JWT,
 * asi que trabaja sobre lo que dice el token y no consulta la base.
 */
public class SegundoFactorPendienteFilter extends OncePerRequestFilter {

    static final String MOTIVO = "Falta el segundo factor: envía el código de tu aplicación de autenticación o uno de "
            + "tus códigos de recuperación a /api/v1/auth/segundo-factor/verificacion.";

    static final String VERIFICACION = "/api/v1/auth/segundo-factor/verificacion";

    private static final Set<String> PERMITIDAS = Set.of(VERIFICACION, "/api/v1/auth/logout", "/api/v1/auth/csrf");

    private final JsonMapper jsonMapper;

    public SegundoFactorPendienteFilter(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (estaPendiente() && !PERMITIDAS.contains(request.getRequestURI())) {
            responder(request, response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private static boolean estaPendiente() {
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
        return autenticacion != null && autenticacion.getPrincipal() instanceof ApiPrincipal principal
                && principal.segundoFactorPendiente();
    }

    private void responder(HttpServletRequest request, HttpServletResponse response) throws IOException {
        ProblemDetail problema = Problemas.de(HttpStatus.UNAUTHORIZED, "Segundo factor requerido", MOTIVO);
        problema.setInstance(URI.create(request.getRequestURI()));

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        jsonMapper.writeValue(response.getOutputStream(), problema);
    }
}
