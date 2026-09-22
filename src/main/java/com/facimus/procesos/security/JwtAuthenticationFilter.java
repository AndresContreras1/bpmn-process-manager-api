package com.facimus.procesos.security;

import java.io.IOException;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Autentica la peticion si trae "Authorization: Bearer <token>" valido y su sesion sigue abierta. La identidad sale de
 * los claims y las sesiones cerradas se miran en memoria, asi que el filtro no consulta la base. Si no, deja pasar
 * sin autenticar: decide SecurityConfig.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String PREFIJO = "Bearer ";

    private final JwtService jwtService;
    private final RevokedSessions revokedSessions;

    public JwtAuthenticationFilter(JwtService jwtService, RevokedSessions revokedSessions) {
        this.jwtService = jwtService;
        this.revokedSessions = revokedSessions;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(PREFIJO)) {
            jwtService.validar(header.substring(PREFIJO.length()))
                    .filter(principal -> !revokedSessions.estaRevocada(principal.sesion()))
                    .ifPresent(principal -> autenticar(principal, request));
        }
        filterChain.doFilter(request, response);
    }

    private void autenticar(ApiPrincipal principal, HttpServletRequest request) {
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, principal.authorities());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
