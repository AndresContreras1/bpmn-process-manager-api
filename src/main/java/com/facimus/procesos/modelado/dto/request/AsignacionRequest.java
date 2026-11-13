package com.facimus.procesos.modelado.dto.request;

import com.facimus.procesos.modelado.model.TipoAsignacion;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** PR 70: a quien le llega una tarea de usuario. ROL la deja en la bandeja de su lane, que es lo de siempre. */
@Schema(description = "Who a user task goes to; ROL leaves it in its lane's role tray")
public record AsignacionRequest(
        @Schema(description = "ROL, PERSONA, INICIADOR or CAMPO", example = "PERSONA")
        @NotNull(message = "El tipo de asignación es obligatorio.") TipoAsignacion tipo,
        @Schema(description = "The person, with PERSONA: an active administrator or editor of the organization",
                example = "5") Long usuarioId,
        @Schema(description = "With CAMPO, the case variable that holds the person, written as a gateway condition "
                + "names it: approver for the start form, tarea.reviewOrder.approver for an earlier task",
                example = "tarea.reviewOrder.approver")
        @Size(max = 200, message = "El campo admite hasta 200 caracteres.") String campo,
        @Schema(description = "Version of the activity read with the last GET; if someone saved a change since, it "
                + "answers 409", example = "0")
        @NotNull(message = "La versión es obligatoria.") Long version) {
}
