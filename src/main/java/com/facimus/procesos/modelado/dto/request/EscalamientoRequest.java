package com.facimus.procesos.modelado.dto.request;

import com.facimus.procesos.modelado.model.AccionDeEscalamiento;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** PR 71: lo que hace un temporizador de borde que no interrumpe. Sin accion, solo abre su camino. */
@Schema(description = "What a non-interrupting boundary timer does with its task when it fires; without accion it only "
        + "opens its path")
public record EscalamientoRequest(
        @Schema(description = "AVISAR notifies the holder and the supervisor; REASIGNAR hands the task to the "
                + "supervisor", example = "REASIGNAR") AccionDeEscalamiento accion,
        @Schema(description = "The supervisor, an active administrator or editor; empty means the process owner",
                example = "5") Long usuarioId,
        @Schema(description = "Version of the event read with the last GET; if someone saved a change since, it "
                + "answers 409", example = "0")
        @NotNull(message = "La versión es obligatoria.") Long version) {
}
