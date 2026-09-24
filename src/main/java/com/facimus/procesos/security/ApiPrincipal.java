package com.facimus.procesos.security;

import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import com.facimus.procesos.common.model.RolAcceso;
import com.facimus.procesos.gestion.dto.response.UsuarioResponse;

/**
 * Identidad del usuario autenticado. El tenant sale de aqui, nunca del request. sesion es el codigo de la sesion que
 * emitio el token (claim sid): cuando la sesion se cierra, el token deja de servir. debeCambiarClave viaja tambien
 * en el token, asi que el filtro que lo exige no consulta la base (D17).
 */
public record ApiPrincipal(Long usuarioId, Long empresaId, RolAcceso rol, String email, String sesion,
        boolean debeCambiarClave) {

    public static ApiPrincipal of(UsuarioResponse usuario, String sesion) {
        return new ApiPrincipal(usuario.id(), usuario.empresaId(), usuario.rolAcceso(), usuario.email(), sesion,
                usuario.debeCambiarClave());
    }

    public List<GrantedAuthority> authorities() {
        return List.of(new SimpleGrantedAuthority(rol.name()));
    }
}
