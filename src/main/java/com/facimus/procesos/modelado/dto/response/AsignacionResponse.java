package com.facimus.procesos.modelado.dto.response;

import com.facimus.procesos.modelado.model.TipoAsignacion;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * PR 70: a quien le llega la tarea. La actividad que espera en la bandeja de su rol no trae ninguna: asi una version
 * publicada antes de este PR sigue teniendo la misma forma.
 */
@Schema(description = "Who a user task goes to when a case reaches it; absent when it waits in its lane's role tray")
public record AsignacionResponse(
        @Schema(description = "PERSONA: a fixed person; INICIADOR: whoever opened the case; CAMPO: the person a case "
                + "variable names", example = "CAMPO") TipoAsignacion tipo,
        @Schema(description = "The fixed person, with PERSONA", example = "5") Long usuarioId,
        @Schema(description = "The case variable that holds the person, with CAMPO: what a PERSONA field of an earlier "
                + "form wrote", example = "tarea.reviewOrder.approver") String campo) {
}
