package com.facimus.procesos.modelado.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A sequence flow of a pool")
public record ArcoResponse(
        @Schema(example = "20") Long id,
        @Schema(example = "Approved") String etiqueta,
        @Schema(example = "payment.status == APPROVED") String condicion,
        @Schema(example = "12") Long origenId,
        @Schema(example = "10") Long destinoId,
        @Schema(description = "Pool that contains both nodes", example = "1") Long poolId) {
}
