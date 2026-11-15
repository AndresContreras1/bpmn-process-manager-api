package com.facimus.procesos.ejecucion.dto.response;

import java.time.LocalDateTime;

import com.facimus.procesos.common.Default;
import com.facimus.procesos.ejecucion.model.EstadoCaso;
import com.facimus.procesos.ejecucion.model.ModoCaso;

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
        @Schema(description = "Id of the user who opened it; empty when a message or a timer did", example = "2")
        Long creadoPor,
        @Schema(description = "PRODUCCION runs on the wall clock and the business calendar; PRUEBA on the store "
                + "clock ticks and the simulated partners. A case runs on a test version of the draft, with "
                + "versionNumero 0, always in PRUEBA", example = "PRUEBA") ModoCaso modo) {

    /** El de antes del PR 68, cuando todos los casos corrian en ticks. */
    public CasoResponse(Long id, Long procesoId, String procesoNombre, int versionNumero, String referencia,
            EstadoCaso estado, int tickInicio, Integer tickFin, LocalDateTime fechaInicio, LocalDateTime fechaFin,
            Long version, Long creadoPor) {
        this(id, procesoId, procesoNombre, versionNumero, referencia, estado, tickInicio, tickFin, fechaInicio,
                fechaFin, version, creadoPor, ModoCaso.PRUEBA);
    }

    @Default
    public CasoResponse {
        // El constructor completo es el que usan MapStruct y Jackson
    }
}
