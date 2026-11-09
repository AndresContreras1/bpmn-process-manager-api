package com.facimus.procesos.security;

import java.io.IOException;
import java.util.Set;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.facimus.procesos.common.security.ApiPrincipal;
import com.facimus.procesos.common.security.ContextoDeTienda;
import com.facimus.procesos.common.security.ContextoDeTienda.Alcance;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * PR 51: de que tienda son las filas de esta peticion, en cuanto se sabe quien la hace y antes de que nada toque la
 * base. Con sesion, de la suya. Las rutas que todavia no tienen tienda, porque buscan a la persona por su correo o
 * por su token, las del sistema, y estan nombradas aqui una por una. Cualquier otra, ninguna fila.
 */
public class ContextoDeTiendaFilter extends OncePerRequestFilter {

    /** Lo que busca a la persona antes de saber de que tienda es. */
    static final Set<String> SIN_TIENDA_TODAVIA = Set.of("/api/v1/auth/login", "/api/v1/auth/refresh",
            "/api/v1/auth/logout", "/api/v1/empresas", "/api/v1/auth/verificacion/confirmar",
            "/api/v1/auth/recuperacion", "/api/v1/auth/recuperacion/confirmar", "/api/v1/auth/invitacion/aceptar",
            "/api/v1/auth/identidades/vinculo");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Alcance anterior = ContextoDeTienda.fijar(alcance(request));
        try {
            filterChain.doFilter(request, response);
        } finally {
            ContextoDeTienda.restaurar(anterior);
        }
    }

    static Alcance alcance(HttpServletRequest request) {
        if ("POST".equals(request.getMethod()) && SIN_TIENDA_TODAVIA.contains(request.getRequestURI())) {
            return Alcance.SISTEMA;
        }
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacion != null && autenticacion.getPrincipal() instanceof ApiPrincipal principal) {
            return Alcance.deLaTienda(principal.empresaId());
        }
        return Alcance.NINGUNA;
    }
}
