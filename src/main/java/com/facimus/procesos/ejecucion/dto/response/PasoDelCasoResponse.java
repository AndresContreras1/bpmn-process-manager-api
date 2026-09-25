package com.facimus.procesos.ejecucion.dto.response;

import java.time.LocalDateTime;
import java.util.Map;

import com.facimus.procesos.ejecucion.model.EstadoActividadCaso;
import com.facimus.procesos.ejecucion.model.TipoNodoCaso;

import io.swagger.v3.oas.annotations.media.Schema;

/** Un paso del caso por un nodo de su version. Los que estan PENDIENTE o EN_ESPERA son sus tokens vivos. */
@Schema(description = "One step of the case through a node of its version")
public record PasoDelCasoResponse(
        @Schema(example = "77") Long id,
        @Schema(description = "Id of the node inside the published version", example = "12") Long nodoId,
        @Schema(example = "Pick and pack items") String nodoNombre,
        @Schema(example = "ACTIVIDAD") TipoNodoCaso tipoNodo,
        @Schema(description = "Kind of activity, gateway or event, as the version had it", example = "USUARIO")
        String subtipo,
        @Schema(description = "Process role of the node's lane: whose tray the task shows up in", example = "2")
        Long rolProcesoId,
        @Schema(example = "EN_ESPERA") EstadoActividadCaso estado,
        @Schema(description = "How many tokens have reached this join", example = "1") int llegadas,
        @Schema(description = "User who took the task", example = "5") Long asignadoA,
        @Schema(example = "0") int tickInicio,
        @Schema(example = "2") Integer tickFin,
        @Schema(description = "What the person handed over when completing the task",
                example = "{\"packedItems\": 3}") Map<String, Object> datosSalida,
        @Schema(example = "0") Long version,
        @Schema(example = "2026-09-24T15:00:00") LocalDateTime fechaCreacion,
        @Schema(description = "Id of the user who completed or took it", example = "5") Long modificadoPor,
        @Schema(example = "2026-09-24T15:10:00") LocalDateTime fechaModificacion) {
}
