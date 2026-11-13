package com.facimus.procesos.modelado.dto.response;

import java.time.LocalDateTime;

import com.facimus.procesos.common.Default;
import com.facimus.procesos.modelado.model.TipoActividad;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "An activity (task) of the process")
public record ActividadResponse(
        @Schema(example = "10") Long id,
        @Schema(description = "BPMN id of the task, stable for its whole life: the id in an exported .bpmn file",
                example = "Activity_1f3a9c0b7e42") String bpmnId,
        @Schema(example = "Pick and pack items") String nombre,
        @Schema(example = "Collect the items and prepare the package.") String descripcion,
        @Schema(example = "USUARIO") TipoActividad tipoActividad,
        @Schema(example = "580") int posicionX,
        @Schema(example = "200") int posicionY,
        @Schema(example = "4") Long laneId,
        @Schema(description = "Send it back when editing; it goes up with every saved change", example = "0")
        Long version,
        @Schema(description = "Id of the user who created it; empty when the system did, like the store registration",
                example = "2") Long creadoPor,
        @Schema(example = "2026-09-21T15:00:00") LocalDateTime fechaCreacion,
        @Schema(description = "Id of the user who saved the last change", example = "5") Long modificadoPor,
        @Schema(example = "2026-09-21T15:30:00") LocalDateTime fechaModificacion,
        @Schema(description = "Sub-process that contains it; empty in the main flow", example = "12") Long padreId,
        @Schema(description = "Process a call activity runs", example = "7") Long procesoLlamadoId,
        @Schema(description = "Typed form of a user task; empty when it has none") FormularioResponse formulario,
        @Schema(description = "Who a user task goes to; empty when it waits in its lane's role tray")
        AsignacionResponse asignacion,
        @Schema(description = "The deadline of a user task; empty when it has none") PlazoResponse plazo) {

    /** El de antes del PR 71, sin plazo. */
    @SuppressWarnings("java:S107") // los campos de la actividad, por su nombre
    public ActividadResponse(Long id, String bpmnId, String nombre, String descripcion, TipoActividad tipoActividad,
            int posicionX, int posicionY, Long laneId, Long version, Long creadoPor, LocalDateTime fechaCreacion,
            Long modificadoPor, LocalDateTime fechaModificacion, Long padreId, Long procesoLlamadoId,
            FormularioResponse formulario, AsignacionResponse asignacion) {
        this(id, bpmnId, nombre, descripcion, tipoActividad, posicionX, posicionY, laneId, version, creadoPor,
                fechaCreacion, modificadoPor, fechaModificacion, padreId, procesoLlamadoId, formulario, asignacion,
                null);
    }

    /** El de antes del PR 70, sin asignacion: espera en la bandeja de su rol. */
    @SuppressWarnings("java:S107") // los campos de la actividad, por su nombre
    public ActividadResponse(Long id, String bpmnId, String nombre, String descripcion, TipoActividad tipoActividad,
            int posicionX, int posicionY, Long laneId, Long version, Long creadoPor, LocalDateTime fechaCreacion,
            Long modificadoPor, LocalDateTime fechaModificacion, Long padreId, Long procesoLlamadoId,
            FormularioResponse formulario) {
        this(id, bpmnId, nombre, descripcion, tipoActividad, posicionX, posicionY, laneId, version, creadoPor,
                fechaCreacion, modificadoPor, fechaModificacion, padreId, procesoLlamadoId, formulario, null, null);
    }

    /** El de antes del PR 69, sin formulario. */
    public ActividadResponse(Long id, String bpmnId, String nombre, String descripcion, TipoActividad tipoActividad,
            int posicionX, int posicionY, Long laneId, Long version, Long creadoPor, LocalDateTime fechaCreacion,
            Long modificadoPor, LocalDateTime fechaModificacion, Long padreId, Long procesoLlamadoId) {
        this(id, bpmnId, nombre, descripcion, tipoActividad, posicionX, posicionY, laneId, version, creadoPor,
                fechaCreacion, modificadoPor, fechaModificacion, padreId, procesoLlamadoId, null, null, null);
    }

    /** El de antes del PR 56, en el flujo principal. */
    public ActividadResponse(Long id, String bpmnId, String nombre, String descripcion, TipoActividad tipoActividad,
            int posicionX, int posicionY, Long laneId, Long version, Long creadoPor, LocalDateTime fechaCreacion,
            Long modificadoPor, LocalDateTime fechaModificacion) {
        this(id, bpmnId, nombre, descripcion, tipoActividad, posicionX, posicionY, laneId, version, creadoPor,
                fechaCreacion, modificadoPor, fechaModificacion, null, null, null, null, null);
    }

    @Default
    public ActividadResponse {
        // El constructor completo es el que usan MapStruct y Jackson
    }
}
