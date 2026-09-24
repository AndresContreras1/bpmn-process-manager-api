package com.facimus.procesos.modelado.dto.response;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A sequence flow of a pool")
public record ArcoResponse(
        @Schema(example = "20") Long id,
        @Schema(example = "Approved") String etiqueta,
        @Schema(example = "payment.status == APPROVED") String condicion,
        @Schema(description = "The flow the gateway takes when no condition holds", example = "false")
        boolean porDefecto,
        @Schema(description = "Order in which the gateway evaluates its outgoing flows", example = "0") int orden,
        @Schema(example = "12") Long origenId,
        @Schema(example = "10") Long destinoId,
        @Schema(description = "Pool that contains both nodes", example = "1") Long poolId,
        @Schema(description = "Send it back when editing; it goes up with every saved change", example = "0")
        Long version,
        @Schema(description = "Id of the user who created it; empty when the system did, like the store registration",
                example = "2") Long creadoPor,
        @Schema(example = "2026-09-21T15:00:00") LocalDateTime fechaCreacion,
        @Schema(description = "Id of the user who saved the last change", example = "5") Long modificadoPor,
        @Schema(example = "2026-09-21T15:30:00") LocalDateTime fechaModificacion) {
}
