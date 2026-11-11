package com.facimus.procesos.modelado.dto.request;

import com.facimus.procesos.gestion.model.ModoProceso;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** PR 55: el modo nuevo del proceso. */
public record ModoProcesoRequest(
        @Schema(description = "EJECUTABLE runs cases; DOCUMENTAL is published and documented but opens none",
                example = "DOCUMENTAL")
        @NotNull(message = "El modo es obligatorio.") ModoProceso modo,
        @Schema(description = "Version of the process read with the last GET", example = "3")
        @NotNull(message = "La versión es obligatoria.") Long version) {
}
