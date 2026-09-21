package com.facimus.procesos.modelado.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Correlation key of a message")
public record CorrelacionRequest(
        @Schema(description = "Data that links the messages of one conversation", example = "orderId")
        @NotBlank(message = "El criterio es obligatorio.")
        @Size(max = 120, message = "El criterio no puede superar 120 caracteres.") String criterio) {
}
