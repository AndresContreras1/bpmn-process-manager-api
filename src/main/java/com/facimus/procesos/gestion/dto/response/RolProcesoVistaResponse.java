package com.facimus.procesos.gestion.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A process role and how much it is used")
public record RolProcesoVistaResponse(
        @Schema(example = "2") Long id,
        @Schema(example = "Warehouse") String nombre,
        @Schema(example = "Picks, packs and ships the orders.") String descripcion,
        @Schema(description = "Active processes with a lane for this role", example = "1") long procesosQueLoUsan,
        @Schema(description = "A role in use cannot be deleted", example = "true") boolean enUso,
        @Schema(description = "Send it back when editing; it goes up with every saved change", example = "0")
        Long version) {
}
