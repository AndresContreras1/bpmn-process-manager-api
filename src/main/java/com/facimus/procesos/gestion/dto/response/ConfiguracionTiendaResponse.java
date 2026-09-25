package com.facimus.procesos.gestion.dto.response;

import java.time.LocalDateTime;

import com.facimus.procesos.gestion.model.ModoSimulacion;
import com.facimus.procesos.gestion.model.PoliticaEstructura;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "What the store decides about itself")
public record ConfiguracionTiendaResponse(
        @Schema(description = "Who can create and edit participants and lanes", example = "ADMINISTRADOR_Y_EDITOR")
        PoliticaEstructura politicaEstructura,
        @Schema(description = "The store's simulation clock, in ticks; it starts at zero and only goes up",
                example = "7") int reloj,
        @Schema(description = "Who moves the clock: whoever is testing, or a job every few seconds",
                example = "MANUAL") ModoSimulacion modoSimulacion,
        @Schema(description = "How the store's simulated partners behave") ParametrosSimulacionResponse simulacion,
        @Schema(description = "Send it back when editing; it goes up with every saved change", example = "0")
        Long version,
        @Schema(example = "2026-09-21T15:30:00") LocalDateTime fechaModificacion,
        @Schema(description = "Id of the user who saved the last change", example = "5") Long modificadoPor) {
}
