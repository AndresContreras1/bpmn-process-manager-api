package com.facimus.procesos.modelado.dto.response;

import java.time.LocalDateTime;

import com.facimus.procesos.modelado.model.TipoGateway;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A gateway of the process")
public record GatewayResponse(
        @Schema(example = "12") Long id,
        @Schema(example = "Payment approved?") String nombre,
        @Schema(example = "EXCLUSIVO") TipoGateway tipoGateway,
        @Schema(example = "420") int posicionX,
        @Schema(example = "80") int posicionY,
        @Schema(example = "3") Long laneId,
        @Schema(description = "Send it back when editing; it goes up with every saved change", example = "0")
        Long version,
        @Schema(description = "Id of the user who created it; empty when the system did, like the store registration",
                example = "2") Long creadoPor,
        @Schema(example = "2026-09-21T15:00:00") LocalDateTime fechaCreacion,
        @Schema(description = "Id of the user who saved the last change", example = "5") Long modificadoPor,
        @Schema(example = "2026-09-21T15:30:00") LocalDateTime fechaModificacion) {
}
