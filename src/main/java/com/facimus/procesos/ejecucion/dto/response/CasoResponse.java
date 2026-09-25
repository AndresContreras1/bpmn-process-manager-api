package com.facimus.procesos.ejecucion.dto.response;

import java.time.LocalDateTime;

import com.facimus.procesos.ejecucion.model.EstadoCaso;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "One execution of a published process: an order")
public record CasoResponse(
        @Schema(example = "42") Long id,
        @Schema(example = "1") Long procesoId,
        @Schema(example = "Order fulfillment") String procesoNombre,
        @Schema(description = "Number of the published version the case runs on", example = "2") int versionNumero,
        @Schema(description = "What the messages of this case are matched by", example = "ORD-1001")
        String referencia,
        @Schema(example = "ABIERTO") EstadoCaso estado,
        @Schema(description = "Store clock tick when the case was opened", example = "0") int tickInicio,
        @Schema(description = "Tick when it closed; empty while it is open", example = "4") Integer tickFin,
        @Schema(example = "2026-09-24T15:00:00") LocalDateTime fechaInicio,
        @Schema(example = "2026-09-24T15:30:00") LocalDateTime fechaFin,
        @Schema(description = "Send it back when editing; it goes up with every saved change", example = "0")
        Long version,
        @Schema(description = "Id of the user who opened it; empty when a message did", example = "2")
        Long creadoPor) {
}
