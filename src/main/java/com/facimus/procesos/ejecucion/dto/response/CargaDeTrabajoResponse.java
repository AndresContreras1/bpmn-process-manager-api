package com.facimus.procesos.ejecucion.dto.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/** PR 70: como esta repartido lo que espera: lo que tiene cada persona y lo que no tiene nadie. */
@Schema(description = "The workload: the waiting tasks each person holds and the ones nobody holds")
public record CargaDeTrabajoResponse(
        @Schema(description = "Waiting tasks nobody holds, in the role trays", example = "3") long sinAsignar,
        @Schema(description = "Each person who holds a waiting task, the busiest first")
        List<CargaPorPersonaResponse> personas) {
}
