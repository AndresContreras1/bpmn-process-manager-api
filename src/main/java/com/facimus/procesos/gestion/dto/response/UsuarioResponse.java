package com.facimus.procesos.gestion.dto.response;

import java.time.LocalDateTime;

import com.facimus.procesos.common.model.RolAcceso;
import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A user of the store; the password never leaves the server")
public record UsuarioResponse(
        @Schema(example = "2") Long id,
        @Schema(example = "Luis Gomez") String nombre,
        @Schema(example = "luis@acme.com") String email,
        @Schema(example = "EDITOR") RolAcceso rolAcceso,
        @Schema(example = "true") boolean activo,
        @Schema(description = "Store the user belongs to", example = "1") Long empresaId,
        @Schema(description = "Send it back when editing; it goes up with every saved change", example = "0")
        Long version,
        @Schema(description = "Id of the user who created it; empty when the system did, like the store registration",
                example = "2") Long creadoPor,
        @Schema(example = "2026-09-21T15:00:00") LocalDateTime fechaCreacion,
        @Schema(description = "Id of the user who saved the last change", example = "5") Long modificadoPor,
        @Schema(example = "2026-09-21T15:30:00") LocalDateTime fechaModificacion,
        @Schema(description = "True while the user still has to change a temporary password", example = "false")
        boolean debeCambiarClave,
        @Schema(description = "The temporary password, answered once and only when it was just generated; it is "
                + "never stored in the clear and no other response carries it", example = "Xk7pQm2r")
        @JsonInclude(JsonInclude.Include.NON_NULL) String claveTemporal) {

    /** La clave temporal solo viaja en la respuesta que la genera; el mapper deja el campo vacio siempre. */
    public UsuarioResponse conClaveTemporal(String clave) {
        return new UsuarioResponse(id, nombre, email, rolAcceso, activo, empresaId, version, creadoPor, fechaCreacion,
                modificadoPor, fechaModificacion, debeCambiarClave, clave);
    }
}
