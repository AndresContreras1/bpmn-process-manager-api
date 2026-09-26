package com.facimus.procesos.gestion.dto.request;

import com.facimus.procesos.gestion.model.ModoSimulacion;
import com.facimus.procesos.gestion.model.PoliticaEstructura;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@Schema(description = "What the store decides about itself")
public record ConfiguracionTiendaRequest(
        @Schema(description = "Who can create and edit participants and lanes", example = "SOLO_ADMINISTRADOR")
        @NotNull(message = "La política de estructura es obligatoria.") PoliticaEstructura politicaEstructura,
        @Schema(description = "Who moves the simulation clock; leave it out to keep the current one",
                example = "AUTOMATICO") ModoSimulacion modoSimulacion,
        @Schema(description = "How the simulated partners behave; leave it out to keep the current ones, send it "
                + "and send all of it")
        @Valid ParametrosSimulacionRequest simulacion,
        @Schema(description = "Version read with the last GET; if someone saved a change since, the edit answers 409",
                example = "0")
        @NotNull(message = "La versión es obligatoria.") Long version) {
}
