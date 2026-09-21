package com.facimus.procesos.modelado.controller.dto;

import jakarta.validation.constraints.NotBlank;

public record CorrelacionRequest(
        @NotBlank(message = "El criterio es obligatorio.") String criterio) {
}
