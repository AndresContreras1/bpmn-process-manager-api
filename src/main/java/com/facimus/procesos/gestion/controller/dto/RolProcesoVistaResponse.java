package com.facimus.procesos.gestion.controller.dto;

import com.facimus.procesos.gestion.service.dto.RolProcesoVista;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A process role and how much it is used")
public record RolProcesoVistaResponse(
        @Schema(example = "2") Long id,
        @Schema(example = "Warehouse") String nombre,
        @Schema(example = "Picks, packs and ships the orders.") String descripcion,
        @Schema(description = "Active processes with a lane for this role", example = "1") long procesosQueLoUsan,
        @Schema(description = "A role in use cannot be deleted", example = "true") boolean enUso) {

    public static RolProcesoVistaResponse of(RolProcesoVista v) {
        return new RolProcesoVistaResponse(v.rol().getId(), v.rol().getNombre(), v.rol().getDescripcion(),
                v.procesosQueLoUsan(), v.enUso());
    }
}
