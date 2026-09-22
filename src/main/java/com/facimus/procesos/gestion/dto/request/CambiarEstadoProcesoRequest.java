package com.facimus.procesos.gestion.dto.request;

import com.facimus.procesos.gestion.model.EstadoProceso;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "State transition of a process")
public record CambiarEstadoProcesoRequest(
        @Schema(description = "Target state; a published process cannot go back to draft", example = "PUBLICADO")
        @NotNull(message = "El estado es obligatorio.") EstadoProceso estado,
        @Schema(description = "Version read with the last GET; if someone saved a change since, the edit answers 409",
                example = "0")
        @NotNull(message = "La versión es obligatoria.") Long version) {
}
