package com.facimus.procesos.modelado.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "A sequence flow between two flow nodes of the same pool")
public record ArcoRequest(
        @Schema(description = "Source node: an activity or a gateway", example = "12")
        @NotNull(message = "El nodo de origen es obligatorio.") Long origenId,
        @Schema(description = "Target node, in the same pool as the source", example = "10")
        @NotNull(message = "El nodo de destino es obligatorio.") Long destinoId,
        @Schema(example = "Approved") String etiqueta,
        @Schema(description = "Required when the target is an exclusive or inclusive gateway",
                example = "payment.status == APPROVED") String condicion) {
}
