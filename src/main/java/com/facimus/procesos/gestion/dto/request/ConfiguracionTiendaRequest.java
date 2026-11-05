package com.facimus.procesos.gestion.dto.request;

import com.facimus.procesos.gestion.model.ModoSimulacion;
import com.facimus.procesos.gestion.model.PoliticaEstructura;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
        @Schema(description = "Minutes a session lasts without being renewed, from 30 to 60; leave it out to keep "
                + "the current one", example = "60")
        @Min(value = 30, message = "Una sesión aguanta sin renovarse de 30 a 60 minutos.")
        @Max(value = 60, message = "Una sesión aguanta sin renovarse de 30 a 60 minutos.")
        Integer inactividadSesionMinutos,
        @Schema(description = "Hours after the login when the user has to sign in again, from 1 to 24; leave it out "
                + "to keep the current one", example = "24")
        @Min(value = 1, message = "Una sesión dura de 1 a 24 horas.")
        @Max(value = 24, message = "Una sesión dura de 1 a 24 horas.")
        Integer duracionSesionHoras,
        @Schema(description = "Version read with the last GET; if someone saved a change since, the edit answers 409",
                example = "0")
        @NotNull(message = "La versión es obligatoria.") Long version) {
}
