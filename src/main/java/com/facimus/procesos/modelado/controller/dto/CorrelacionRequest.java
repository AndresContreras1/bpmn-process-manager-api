package com.facimus.procesos.modelado.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Correlation key of a message")
public record CorrelacionRequest(
        @Schema(description = "Data that links the messages of one conversation", example = "orderId")
        @NotBlank(message = "El criterio es obligatorio.") String criterio) {
}
