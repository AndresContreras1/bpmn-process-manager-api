package com.facimus.procesos.modelado.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** PR 56: el proceso que corre una actividad de llamada. */
public record LlamadaRequest(
        @Schema(description = "Process of the store it runs; empty leaves it without one", example = "7")
        Long procesoId,
        @Schema(description = "Version read with the last GET", example = "0")
        @NotNull(message = "La versión es obligatoria.") Long version) {
}
