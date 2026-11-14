package com.facimus.procesos.ejecucion.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** PR 71: cuantos casos con plazo terminaron a tiempo y cuantos tarde. */
@Schema(description = "How many cases with a deadline finished on time and how many late")
public record CumplimientoDeCasosResponse(
        @Schema(description = "Finished before their deadline", example = "30") long aTiempo,
        @Schema(description = "Finished after their deadline, or still open past it", example = "2") long tarde) {
}
