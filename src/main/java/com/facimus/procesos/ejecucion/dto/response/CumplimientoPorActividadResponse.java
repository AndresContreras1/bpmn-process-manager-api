package com.facimus.procesos.ejecucion.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** PR 71: cuantas tareas de una actividad con plazo se hicieron a tiempo y cuantas tarde. */
@Schema(description = "How many tasks of one activity with a deadline were done on time and how many late")
public record CumplimientoPorActividadResponse(
        @Schema(example = "1") Long procesoId,
        @Schema(example = "Order fulfillment") String procesoNombre,
        @Schema(description = "Id of the node inside the published versions", example = "12") Long nodoId,
        @Schema(example = "Pick and pack items") String nodoNombre,
        @Schema(description = "Completed before their deadline", example = "18") long aTiempo,
        @Schema(description = "Completed after their deadline, or still waiting past it", example = "3") long tarde) {
}
