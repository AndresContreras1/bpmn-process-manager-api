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

import com.facimus.procesos.common.api.Problemas;
import com.facimus.procesos.common.security.ApiPrincipal;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

/**
 * PR 49: quien entra en una tienda que le exige un segundo factor y todavia no lo tiene solo puede inscribirlo: verlo,
 * pedir el secreto, confirmarlo y, si hace falta, confirmar su identidad y cambiar una clave temporal. Al renovar la
 * sesion despues de confirmarlo, el token nuevo ya no lo lleva. Trabaja sobre lo que dice el token.
 */
public class InscripcionObligatoriaFilter extends OncePerRequestFilter {

    static final String MOTIVO = "Tu tienda exige un segundo factor: inscribe tu aplicación de autenticación en "
            + "/api/v1/auth/segundo-factor/totp, confírmala y renueva la sesión.";

    /** Lo minimo para inscribirlo y seguir: el segundo factor propio, la sesion y la clave. */
    private static final Set<String> PERMITIDAS = Set.of("/api/v1/auth/segundo-factor",
            "/api/v1/auth/segundo-factor/totp", "/api/v1/auth/segundo-factor/totp/confirmacion",
            "/api/v1/auth/reautenticacion", "/api/v1/auth/password", "/api/v1/auth/refresh", "/api/v1/auth/logout",
            "/api/v1/auth/csrf");

    private final JsonMapper jsonMapper;

    public InscripcionObligatoriaFilter(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (debeInscribirlo() && !PERMITIDAS.contains(request.getRequestURI())) {
            responder(request, response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private static boolean debeInscribirlo() {
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
        return autenticacion != null && autenticacion.getPrincipal() instanceof ApiPrincipal principal
                && principal.debeInscribirSegundoFactor();
    }

    private void responder(HttpServletRequest request, HttpServletResponse response) throws IOException {
        ProblemDetail problema = Problemas.de(HttpStatus.FORBIDDEN, "Segundo factor obligatorio", MOTIVO);
        problema.setInstance(URI.create(request.getRequestURI()));

        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        jsonMapper.writeValue(response.getOutputStream(), problema);
    }
}
