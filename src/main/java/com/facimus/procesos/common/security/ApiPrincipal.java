package com.facimus.procesos.common.security;

import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import com.facimus.procesos.common.model.RolAcceso;

/**
 * Identidad del usuario autenticado. El tenant sale de aqui, nunca del request. sesion es el codigo de la sesion que
 * emitio el token (claim sid): cuando la sesion se cierra, el token deja de servir. debeCambiarClave viaja tambien
 * en el token, asi que el filtro que lo exige no consulta la base (D17).
 */
public record ApiPrincipal(Long usuarioId, Long empresaId, RolAcceso rol, String email, String sesion,
        boolean debeCambiarClave) {

    public List<GrantedAuthority> authorities() {
        return List.of(new SimpleGrantedAuthority(rol.name()));
    }
}
