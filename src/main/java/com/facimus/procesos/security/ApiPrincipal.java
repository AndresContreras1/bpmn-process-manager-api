package com.facimus.procesos.security;

import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import com.facimus.procesos.gestion.model.RolAcceso;
import com.facimus.procesos.gestion.model.Usuario;

/** Identidad del usuario autenticado. El tenant sale de aqui, nunca del request. */
public record ApiPrincipal(Long usuarioId, Long empresaId, RolAcceso rol, String email) {

    public static ApiPrincipal of(Usuario usuario) {
        return new ApiPrincipal(usuario.getId(), usuario.getEmpresa().getId(), usuario.getRolAcceso(),
                usuario.getEmail());
    }

    public List<GrantedAuthority> authorities() {
        return List.of(new SimpleGrantedAuthority(rol.name()));
    }
}
