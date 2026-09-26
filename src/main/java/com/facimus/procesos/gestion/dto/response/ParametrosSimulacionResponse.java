package com.facimus.procesos.gestion.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "How the store's simulated partners behave")
public record ParametrosSimulacionResponse(
        @Schema(description = "Where every decision of the partners comes from", example = "42") long semilla,
        @Schema(example = "10") int tasaRechazoPagos,
        @Schema(example = "1") int ticksRespuestaPagos,
        @Schema(description = "When to decline a payment, in the language of the flow conditions",
                example = "total > 5000") String reglaRechazoPagos,
        @Schema(example = "1") int ticksRespuestaTransporte,
        @Schema(example = "3") int ticksEntrega,
        @Schema(example = "5") int tasaPerdidaEnvios,
        @Schema(example = "2") int tasaFalloNotificaciones) {
}
