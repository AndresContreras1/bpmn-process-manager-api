package com.facimus.procesos.ejecucion.dto.response;

import java.time.LocalDateTime;
import java.util.Map;

import com.facimus.procesos.common.Default;
import com.facimus.procesos.ejecucion.model.EstadoActividadCaso;
import com.facimus.procesos.ejecucion.model.EstadoPlazo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Una tarea de la bandeja: una actividad de usuario que espera a que alguien la complete. Trae de que caso y de que
 * proceso es, porque una bandeja se lee sin haber abierto ningun caso.
 */
@Schema(description = "A task: a user activity waiting for someone to complete it")
public record TareaResponse(
        @Schema(example = "77") Long id,
        @Schema(example = "42") Long casoId,
        @Schema(example = "ORD-1001") String casoReferencia,
        @Schema(example = "1") Long procesoId,
        @Schema(example = "Order fulfillment") String procesoNombre,
        @Schema(description = "Id of the node inside the published version", example = "12") Long nodoId,
        @Schema(example = "Pick and pack items") String nodoNombre,
        @Schema(description = "Process role whose tray it shows up in", example = "2") Long rolProcesoId,
        @Schema(example = "EN_ESPERA") EstadoActividadCaso estado,
        @Schema(description = "Who holds it, the only one who completes it; empty while it waits in its role tray",
                example = "5") Long asignadoA,
        @Schema(example = "0") int tickInicio,
        @Schema(example = "2") Integer tickFin,
        @Schema(description = "What the person handed over when completing it", example = "{\"packedItems\": 3}")
        Map<String, Object> datosSalida,
        @Schema(example = "0") Long version,
        @Schema(example = "2026-09-24T15:00:00") LocalDateTime fechaCreacion,
        @Schema(description = "Who delegated it to its holder; it goes back to them if the holder releases it",
                example = "3") Long delegadaPor,
        @Schema(description = "When it is due, in UTC, if its activity has a deadline and the case runs in "
                + "PRODUCCION", example = "2026-01-14T20:00:00") LocalDateTime venceEn,
        @Schema(description = "The store clock tick when it is due, if its activity has a deadline and the case runs "
                + "in PRUEBA", example = "21") Integer venceTick,
        @Schema(description = "A_TIEMPO, POR_VENCER (the last quarter of its deadline) or VENCIDA; empty without a "
                + "deadline. A completed task keeps the one it reached: VENCIDA means it was completed late",
                example = "A_TIEMPO") EstadoPlazo estadoPlazo) {

    /** El de antes del PR 71, sin plazo. */
    @SuppressWarnings("java:S107") // los campos de la tarea, por su nombre
    public TareaResponse(Long id, Long casoId, String casoReferencia, Long procesoId, String procesoNombre,
            Long nodoId, String nodoNombre, Long rolProcesoId, EstadoActividadCaso estado, Long asignadoA,
            int tickInicio, Integer tickFin, Map<String, Object> datosSalida, Long version,
            LocalDateTime fechaCreacion, Long delegadaPor) {
        this(id, casoId, casoReferencia, procesoId, procesoNombre, nodoId, nodoNombre, rolProcesoId, estado,
                asignadoA, tickInicio, tickFin, datosSalida, version, fechaCreacion, delegadaPor, null, null, null);
    }

    /** El de antes del PR 70, sin delegacion. */
    @SuppressWarnings("java:S107") // los campos de la tarea, por su nombre
    public TareaResponse(Long id, Long casoId, String casoReferencia, Long procesoId, String procesoNombre,
            Long nodoId, String nodoNombre, Long rolProcesoId, EstadoActividadCaso estado, Long asignadoA,
            int tickInicio, Integer tickFin, Map<String, Object> datosSalida, Long version,
            LocalDateTime fechaCreacion) {
        this(id, casoId, casoReferencia, procesoId, procesoNombre, nodoId, nodoNombre, rolProcesoId, estado,
                asignadoA, tickInicio, tickFin, datosSalida, version, fechaCreacion, null, null, null, null);
    }

    @Default
    public TareaResponse {
        // El constructor completo es el que usan MapStruct y Jackson
    }
}
