package com.facimus.procesos.modelado.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "A lane of a pool, assigned to one process role")
public record LaneRequest(
        @Schema(example = "Warehouse")
        @NotBlank(message = "El nombre es obligatorio.") String nombre,
        @Schema(description = "Process role of the same store", example = "2")
        @NotNull(message = "El rol de proceso es obligatorio.") Long rolProcesoId) {
}
