package com.facimus.procesos.modelado.dto.response;

import com.facimus.procesos.modelado.model.AccionDeEscalamiento;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * PR 71: lo que un temporizador de borde que no interrumpe hace con la tarea al saltar. Uno que solo abre su camino no
 * trae ninguno.
 */
@Schema(description = "What a non-interrupting boundary timer does with its task when it fires, besides opening its "
        + "path; absent when it only opens the path")
public record EscalamientoResponse(
        @Schema(description = "AVISAR notifies the holder and the supervisor; REASIGNAR hands the task to the "
                + "supervisor", example = "REASIGNAR") AccionDeEscalamiento accion,
        @Schema(description = "The supervisor; empty means the process owner", example = "5") Long usuarioId) {
}
