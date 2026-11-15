package com.facimus.procesos.gestion.dto.response;

import java.time.LocalDateTime;

import com.facimus.procesos.common.Default;
import com.facimus.procesos.gestion.model.ModoSimulacion;
import com.facimus.procesos.gestion.model.PoliticaEstructura;
import com.facimus.procesos.gestion.model.PoliticaSegundoFactor;

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
        @Schema(description = "Minutes a session lasts without being renewed", example = "60")
        int inactividadSesionMinutos,
        @Schema(description = "Hours after the login when the user has to sign in again", example = "24")
        int duracionSesionHoras,
        @Schema(description = "Who has to use a second factor to work: whoever needs one and has none can only enroll "
                + "it", example = "OPCIONAL") PoliticaSegundoFactor politicaSegundoFactor,
        @Schema(description = "True when the people of the store sign in only with Google or Microsoft; the "
                + "administrators keep their password", example = "false") boolean soloEntradaExterna,
        @Schema(description = "Send it back when editing; it goes up with every saved change", example = "0")
        Long version,
        @Schema(example = "2026-09-21T15:30:00") LocalDateTime fechaModificacion,
        @Schema(description = "Id of the user who saved the last change", example = "5") Long modificadoPor,
        @Schema(description = "True when an administrator may complete a task another person holds; the audit trail "
                + "records each time", example = "false") boolean administradorCompletaPorOtro) {

    /** El de antes del PR 70, sin la politica de completar por otro: apagada. */
    @SuppressWarnings("java:S107") // cada parte de la configuracion, por su nombre
    public ConfiguracionTiendaResponse(PoliticaEstructura politicaEstructura, int reloj, ModoSimulacion modoSimulacion,
            ParametrosSimulacionResponse simulacion, int inactividadSesionMinutos, int duracionSesionHoras,
            PoliticaSegundoFactor politicaSegundoFactor, boolean soloEntradaExterna, Long version,
            LocalDateTime fechaModificacion, Long modificadoPor) {
        this(politicaEstructura, reloj, modoSimulacion, simulacion, inactividadSesionMinutos, duracionSesionHoras,
                politicaSegundoFactor, soloEntradaExterna, version, fechaModificacion, modificadoPor, false);
    }

    @Default
    public ConfiguracionTiendaResponse {
        // El constructor completo es el que usan MapStruct y Jackson
    }
}
