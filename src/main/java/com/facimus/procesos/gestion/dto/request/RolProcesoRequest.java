package com.facimus.procesos.gestion.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Process role, the responsibility that a lane represents")
public record RolProcesoRequest(
        @Schema(description = "Name, unique within the store", example = "Warehouse")
        @NotBlank(message = "El nombre del rol es obligatorio.")
        @Size(max = 80, message = "El nombre no puede superar 80 caracteres.") String nombre,
        @Schema(example = "Picks, packs and ships the orders.")
        @Size(max = 500, message = "La descripcion no puede superar 500 caracteres.") String descripcion) {
}
