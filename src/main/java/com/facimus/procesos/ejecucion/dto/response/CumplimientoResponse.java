package com.facimus.procesos.ejecucion.dto.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * PR 71: el cumplimiento de los plazos. Una tarea o un caso cuenta cuando ya se sabe como le fue: a tiempo si se
 * completo antes de vencer, tarde si se completo despues o si sigue esperando pasado su plazo. Lo que espera y todavia
 * no vence no cuenta, ni lo que se cancelo, ni las pruebas de un borrador.
 */
@Schema(description = "How deadlines were kept: a task or a case counts once its outcome is known, on time if done "
        + "before its deadline, late if done after it or still waiting past it")
public record CumplimientoResponse(
        @Schema(description = "Tasks done on time", example = "40") long tareasATiempo,
        @Schema(description = "Tasks done late, or still waiting past their deadline", example = "5")
        long tareasTarde,
        @Schema(description = "Cases on time and late") CumplimientoDeCasosResponse casos,
        @Schema(description = "Tasks by process") List<CumplimientoPorProcesoResponse> porProceso,
        @Schema(description = "Tasks by activity") List<CumplimientoPorActividadResponse> porActividad,
        @Schema(description = "Tasks by the person who did or holds them") List<CumplimientoPorPersonaResponse> porPersona) {
}
