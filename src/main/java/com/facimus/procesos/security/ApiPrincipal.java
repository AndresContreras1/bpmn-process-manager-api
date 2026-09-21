package com.facimus.procesos.security;

import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import com.facimus.procesos.gestion.dto.response.UsuarioResponse;
import com.facimus.procesos.gestion.model.RolAcceso;

/** Identidad del usuario autenticado. El tenant sale de aqui, nunca del request. */
public record ApiPrincipal(Long usuarioId, Long empresaId, RolAcceso rol, String email) {

    public static ApiPrincipal of(UsuarioResponse usuario) {
        return new ApiPrincipal(usuario.id(), usuario.empresaId(), usuario.rolAcceso(), usuario.email());
    }

    public List<GrantedAuthority> authorities() {
        return List.of(new SimpleGrantedAuthority(rol.name()));
    }
}
