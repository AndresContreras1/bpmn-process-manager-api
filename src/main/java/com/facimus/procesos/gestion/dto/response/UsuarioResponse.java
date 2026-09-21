package com.facimus.procesos.gestion.dto.response;

import com.facimus.procesos.gestion.model.RolAcceso;
import com.facimus.procesos.gestion.model.Usuario;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A user of the store; the password never leaves the server")
public record UsuarioResponse(
        @Schema(example = "2") Long id,
        @Schema(example = "Luis Gomez") String nombre,
        @Schema(example = "luis@acme.com") String email,
        @Schema(example = "EDITOR") RolAcceso rolAcceso,
        @Schema(example = "true") boolean activo) {

    public static UsuarioResponse of(Usuario u) {
        return new UsuarioResponse(u.getId(), u.getNombre(), u.getEmail(), u.getRolAcceso(), u.isActivo());
    }
}
