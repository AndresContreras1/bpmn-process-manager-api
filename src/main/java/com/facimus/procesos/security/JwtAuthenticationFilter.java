package com.facimus.procesos.security;

import java.io.IOException;
import java.util.Optional;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.gestion.model.Usuario;
import com.facimus.procesos.gestion.service.UsuarioService;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Autentica la peticion si trae "Authorization: Bearer <token>" valido y el usuario sigue activo.
 * Si no, deja pasar sin autenticar: decide SecurityConfig.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String PREFIJO = "Bearer ";

    private final JwtService jwtService;
    private final UsuarioService usuarioService;

    public JwtAuthenticationFilter(JwtService jwtService, UsuarioService usuarioService) {
        this.jwtService = jwtService;
        this.usuarioService = usuarioService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(PREFIJO)) {
            jwtService.validar(header.substring(PREFIJO.length()))
                    .flatMap(this::usuarioVigente)
                    .ifPresent(principal -> autenticar(principal, request));
        }
        filterChain.doFilter(request, response);
    }

    private Optional<ApiPrincipal> usuarioVigente(Claims claims) {
        Long empresaId = claims.get(JwtService.CLAIM_EMPRESA_ID, Long.class);
        Long usuarioId = claims.get(JwtService.CLAIM_USUARIO_ID, Long.class);
        if (empresaId == null || usuarioId == null) {
            return Optional.empty();
        }
        try {
            Usuario usuario = usuarioService.obtener(empresaId, usuarioId);
            return usuario.isActivo() ? Optional.of(ApiPrincipal.of(usuario)) : Optional.empty();
        } catch (RecursoNoEncontradoException e) {
            return Optional.empty();
        }
    }

    private void autenticar(ApiPrincipal principal, HttpServletRequest request) {
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, principal.authorities());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
