package com.facimus.procesos.ejecucion.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/** PR 70: por que se suelta una tarea. Es opcional: soltarla es devolverla, no quitarsela a nadie. */
@Schema(description = "Why the task is released; optional")
public record SoltarTareaRequest(
        @Schema(description = "Why; it goes to the case timeline", example = "I cannot reach the customer")
        @Size(max = 500, message = "El motivo admite hasta 500 caracteres.") String motivo) {
}
