package com.facimus.procesos.security;

import java.io.IOException;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import com.facimus.procesos.common.security.ApiPrincipal;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Autentica la peticion si trae en la cookie de acceso un token valido y su sesion sigue abierta (D29). La identidad
 * sale de los claims y las sesiones cerradas se miran en memoria, asi que el filtro no consulta la base. Si no, deja
 * pasar sin autenticar: decide SecurityConfig. Un {@code Authorization: Bearer} queda para las claves de API.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final RevokedSessions revokedSessions;

    public JwtAuthenticationFilter(JwtService jwtService, RevokedSessions revokedSessions) {
        this.jwtService = jwtService;
        this.revokedSessions = revokedSessions;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        CookiesDeSesion.leer(request, CookiesDeSesion.ACCESO)
                .flatMap(jwtService::validar)
                .filter(principal -> !revokedSessions.estaRevocada(principal.sesion()))
                .ifPresent(principal -> autenticar(principal, request));
        filterChain.doFilter(request, response);
    }

    private void autenticar(ApiPrincipal principal, HttpServletRequest request) {
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, principal.authorities());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
