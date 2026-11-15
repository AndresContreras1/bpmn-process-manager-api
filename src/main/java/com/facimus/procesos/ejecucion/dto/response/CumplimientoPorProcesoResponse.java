package com.facimus.procesos.ejecucion.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** PR 71: cuantas tareas con plazo de un proceso se hicieron a tiempo y cuantas tarde. */
@Schema(description = "How many tasks with a deadline of one process were done on time and how many late")
public record CumplimientoPorProcesoResponse(
        @Schema(example = "1") Long procesoId,
        @Schema(example = "Order fulfillment") String procesoNombre,
        @Schema(description = "Completed before their deadline", example = "40") long aTiempo,
        @Schema(description = "Completed after their deadline, or still waiting past it", example = "5") long tarde) {
}
