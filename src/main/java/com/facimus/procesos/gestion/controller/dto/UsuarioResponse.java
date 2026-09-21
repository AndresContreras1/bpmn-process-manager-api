package com.facimus.procesos.gestion.controller.dto;

import com.facimus.procesos.gestion.model.RolAcceso;
import com.facimus.procesos.gestion.model.Usuario;

public record UsuarioResponse(Long id, String nombre, String email, RolAcceso rolAcceso, boolean activo) {

    public static UsuarioResponse of(Usuario u) {
        return new UsuarioResponse(u.getId(), u.getNombre(), u.getEmail(), u.getRolAcceso(), u.isActivo());
    }
}
