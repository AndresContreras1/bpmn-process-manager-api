package com.facimus.procesos.gestion.dto.request;

import com.facimus.procesos.gestion.model.PoliticaEstructura;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "What the store decides about itself")
public record ConfiguracionTiendaRequest(
        @Schema(description = "Who can create and edit participants and lanes", example = "SOLO_ADMINISTRADOR")
        @NotNull(message = "La política de estructura es obligatoria.") PoliticaEstructura politicaEstructura,
        @Schema(description = "Version read with the last GET; if someone saved a change since, the edit answers 409",
                example = "0")
        @NotNull(message = "La versión es obligatoria.") Long version) {
}
