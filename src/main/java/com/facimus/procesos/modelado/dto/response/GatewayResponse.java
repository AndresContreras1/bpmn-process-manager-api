package com.facimus.procesos.modelado.dto.response;

import java.time.LocalDateTime;

import com.facimus.procesos.common.Default;
import com.facimus.procesos.modelado.model.TipoGateway;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A gateway of the process")
public record GatewayResponse(
        @Schema(example = "12") Long id,
        @Schema(description = "BPMN id of the gateway, stable for its whole life: the id in an exported .bpmn file",
                example = "Gateway_0b9e44a1c7d2") String bpmnId,
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
        @Schema(example = "2026-09-21T15:30:00") LocalDateTime fechaModificacion,
        @Schema(description = "Sub-process that contains it; empty in the main flow", example = "12") Long padreId) {

    /** El de antes del PR 56, en el flujo principal. */
    public GatewayResponse(Long id, String bpmnId, String nombre, TipoGateway tipoGateway, int posicionX,
            int posicionY, Long laneId, Long version, Long creadoPor, LocalDateTime fechaCreacion, Long modificadoPor,
            LocalDateTime fechaModificacion) {
        this(id, bpmnId, nombre, tipoGateway, posicionX, posicionY, laneId, version, creadoPor, fechaCreacion,
                modificadoPor, fechaModificacion, null);
    }

    @Default
    public GatewayResponse {
        // El constructor completo es el que usan MapStruct y Jackson
    }
}
