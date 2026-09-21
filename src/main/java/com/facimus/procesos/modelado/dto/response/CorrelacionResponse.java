package com.facimus.procesos.modelado.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Correlation key of a message")
public record CorrelacionResponse(
        @Schema(example = "3") Long id,
        @Schema(example = "orderId") String criterio,
        @Schema(example = "5") Long mensajeId) {
}
