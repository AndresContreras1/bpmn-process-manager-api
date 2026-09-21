package com.facimus.procesos.modelado.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record LaneRequest(
        @NotBlank(message = "El nombre es obligatorio.") String nombre,
        @NotNull(message = "El rol de proceso es obligatorio.") Long rolProcesoId) {
}
