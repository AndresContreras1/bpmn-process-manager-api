package com.facimus.procesos.gestion.dto.request;

import com.facimus.procesos.gestion.model.EstadoVersion;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "State change of a published version")
public record CambiarEstadoVersionRequest(
        @Schema(description = "Only RETIRADA: a version that was retired does not come back", example = "RETIRADA")
        @NotNull(message = "El estado es obligatorio.") EstadoVersion estado) {
}
