package com.facimus.procesos.security;

import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import com.facimus.procesos.gestion.dto.response.UsuarioResponse;
import com.facimus.procesos.gestion.model.RolAcceso;

/**
 * Identidad del usuario autenticado. El tenant sale de aqui, nunca del request. sesion es el codigo de la sesion que
 * emitio el token (claim sid): cuando la sesion se cierra, el token deja de servir.
 */
public record ApiPrincipal(Long usuarioId, Long empresaId, RolAcceso rol, String email, String sesion) {

    public static ApiPrincipal of(UsuarioResponse usuario, String sesion) {
        return new ApiPrincipal(usuario.id(), usuario.empresaId(), usuario.rolAcceso(), usuario.email(), sesion);
    }

    public List<GrantedAuthority> authorities() {
        return List.of(new SimpleGrantedAuthority(rol.name()));
    }
}
