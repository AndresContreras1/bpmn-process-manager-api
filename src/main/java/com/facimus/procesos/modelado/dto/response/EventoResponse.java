package com.facimus.procesos.modelado.dto.response;

import java.time.LocalDateTime;

import com.facimus.procesos.common.Default;
import com.facimus.procesos.modelado.model.TipoEvento;
import com.facimus.procesos.modelado.model.TipoTemporizador;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "An event of the process")
public record EventoResponse(
        @Schema(example = "14") Long id,
        @Schema(description = "BPMN id of the event, stable for its whole life: the id in an exported .bpmn file",
                example = "Event_5d0a2f9e81c4") String bpmnId,
        @Schema(example = "Order received") String nombre,
        @Schema(example = "MENSAJE_INICIO") TipoEvento tipoEvento,
        @Schema(example = "20") int posicionX,
        @Schema(example = "80") int posicionY,
        @Schema(example = "3") Long laneId,
        @Schema(description = "Send it back when editing; it goes up with every saved change", example = "0")
        Long version,
        @Schema(description = "Id of the user who created it; empty when the system did, like the store registration",
                example = "2") Long creadoPor,
        @Schema(example = "2026-09-21T15:00:00") LocalDateTime fechaCreacion,
        @Schema(description = "Id of the user who saved the last change", example = "5") Long modificadoPor,
        @Schema(example = "2026-09-21T15:30:00") LocalDateTime fechaModificacion,
        @Schema(description = "How a timer event says when it fires; empty for any other event", example = "DURACION")
        TipoTemporizador tipoTemporizador,
        @Schema(description = "ISO 8601 duration, date or cycle of a timer event", example = "PT2H") String temporizador,
        @Schema(description = "Activity a boundary event is attached to", example = "10") Long adjuntoAId,
        @Schema(description = "Whether a boundary event interrupts the activity when it fires", example = "true")
        Boolean interrumpe,
        @Schema(description = "Sub-process that contains it; empty in the main flow", example = "12") Long padreId,
        @Schema(description = "Typed form of a start event opened by hand; empty when it has none")
        FormularioResponse formulario) {

    /** El de antes del PR 69, sin formulario. */
    public EventoResponse(Long id, String bpmnId, String nombre, TipoEvento tipoEvento, int posicionX, int posicionY,
            Long laneId, Long version, Long creadoPor, LocalDateTime fechaCreacion, Long modificadoPor,
            LocalDateTime fechaModificacion, TipoTemporizador tipoTemporizador, String temporizador, Long adjuntoAId,
            Boolean interrumpe, Long padreId) {
        this(id, bpmnId, nombre, tipoEvento, posicionX, posicionY, laneId, version, creadoPor, fechaCreacion,
                modificadoPor, fechaModificacion, tipoTemporizador, temporizador, adjuntoAId, interrumpe, padreId,
                null);
    }

    /** El de antes del PR 56, en el flujo principal. */
    public EventoResponse(Long id, String bpmnId, String nombre, TipoEvento tipoEvento, int posicionX, int posicionY,
            Long laneId, Long version, Long creadoPor, LocalDateTime fechaCreacion, Long modificadoPor,
            LocalDateTime fechaModificacion, TipoTemporizador tipoTemporizador, String temporizador, Long adjuntoAId,
            Boolean interrumpe) {
        this(id, bpmnId, nombre, tipoEvento, posicionX, posicionY, laneId, version, creadoPor, fechaCreacion,
                modificadoPor, fechaModificacion, tipoTemporizador, temporizador, adjuntoAId, interrumpe, null, null);
    }

    /** El de antes del PR 55, sin temporizador. */
    public EventoResponse(Long id, String bpmnId, String nombre, TipoEvento tipoEvento, int posicionX, int posicionY,
            Long laneId, Long version, Long creadoPor, LocalDateTime fechaCreacion, Long modificadoPor,
            LocalDateTime fechaModificacion) {
        this(id, bpmnId, nombre, tipoEvento, posicionX, posicionY, laneId, version, creadoPor, fechaCreacion,
                modificadoPor, fechaModificacion, null, null, null, null, null, null);
    }

    @Default
    public EventoResponse {
        // El constructor completo es el que usan MapStruct y Jackson
    }
}
