package com.facimus.procesos.gestion.dto.response;

import java.time.LocalDateTime;

import com.facimus.procesos.gestion.model.RolAcceso;

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
        @Schema(example = "2026-09-21T15:30:00") LocalDateTime fechaModificacion) {
}
