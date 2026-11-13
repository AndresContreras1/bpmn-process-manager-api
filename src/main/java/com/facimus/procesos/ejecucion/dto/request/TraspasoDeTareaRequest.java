package com.facimus.procesos.ejecucion.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** PR 70: a quien pasa una tarea y por que. Reasignar y delegar llevan los dos su motivo a la linea de tiempo. */
@Schema(description = "Who a task goes to and why")
public record TraspasoDeTareaRequest(
        @Schema(description = "An active administrator or editor of the organization", example = "5")
        @NotNull(message = "La persona es obligatoria.") Long usuarioId,
        @Schema(description = "Why; it goes to the case timeline", example = "Ana is on leave this week")
        @NotBlank(message = "El motivo es obligatorio.")
        @Size(max = 500, message = "El motivo admite hasta 500 caracteres.") String motivo) {
}
