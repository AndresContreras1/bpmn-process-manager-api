package com.facimus.procesos.gestion.dto.response;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A process role and how much it is used")
public record RolProcesoVistaResponse(
        @Schema(example = "2") Long id,
        @Schema(example = "Warehouse") String nombre,
        @Schema(example = "Picks, packs and ships the orders.") String descripcion,
        @Schema(description = "Active processes with a lane for this role", example = "1") long procesosQueLoUsan,
        @Schema(description = "A role in use cannot be deleted", example = "true") boolean enUso,
        @Schema(description = "Send it back when editing; it goes up with every saved change", example = "0")
        Long version,
        @Schema(description = "Id of the user who created it; empty when the system did, like the store registration",
                example = "2") Long creadoPor,
        @Schema(example = "2026-09-21T15:00:00") LocalDateTime fechaCreacion,
        @Schema(description = "Id of the user who saved the last change", example = "5") Long modificadoPor,
        @Schema(example = "2026-09-21T15:30:00") LocalDateTime fechaModificacion) {
}
