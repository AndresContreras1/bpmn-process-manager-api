package com.facimus.procesos.gestion.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Process role, the responsibility that a lane represents")
public record RolProcesoRequest(
        @Schema(description = "Name, unique within the store", example = "Warehouse")
        @NotBlank(message = "El nombre del rol es obligatorio.") String nombre,
        @Schema(example = "Picks, packs and ships the orders.") String descripcion) {
}
