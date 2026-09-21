package com.facimus.procesos.modelado.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "A lane of a pool, assigned to one process role")
public record LaneRequest(
        @Schema(example = "Warehouse")
        @NotBlank(message = "El nombre es obligatorio.")
        @Size(max = 120, message = "El nombre no puede superar 120 caracteres.") String nombre,
        @Schema(description = "Process role of the same store", example = "2")
        @NotNull(message = "El rol de proceso es obligatorio.") Long rolProcesoId) {
}
