package com.facimus.procesos.ejecucion.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * PR 71: cuantas tareas con plazo hizo una persona a tiempo y cuantas tarde; las que siguen esperando pasado su plazo
 * cuentan para quien las tiene.
 */
@Schema(description = "How many tasks with a deadline one person did on time and how many late; a task still waiting "
        + "past its deadline counts for whoever holds it")
public record CumplimientoPorPersonaResponse(
        @Schema(example = "5") Long usuarioId,
        @Schema(example = "Luis Gomez") String nombre,
        @Schema(description = "Completed before their deadline", example = "12") long aTiempo,
        @Schema(description = "Completed after their deadline, or held past it", example = "1") long tarde) {
}
