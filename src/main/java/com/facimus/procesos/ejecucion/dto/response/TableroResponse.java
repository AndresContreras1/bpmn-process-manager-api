package com.facimus.procesos.ejecucion.dto.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Como va la operacion: de un proceso o de la tienda entera. Todo sale de la base y en un numero fijo de
 * consultas agrupadas, no de una por caso.
 */
@Schema(description = "How operations are going, for one process or for the whole store")
public record TableroResponse(
        @Schema(description = "Process it is about; empty when it is the whole store", example = "1")
        Long procesoId,
        @Schema(example = "20") long casos,
        @Schema(description = "Cases by state") List<CasosPorEstadoResponse> casosPorEstado,
        @Schema(description = "How long a finished order takes") CicloDeCasoResponse ciclo,
        @Schema(description = "Work waiting in each role's tray") List<TareasPorRolResponse> tareasPorRol,
        @Schema(description = "Sent messages by state") List<MensajesPorEstadoResponse> salientes,
        @Schema(description = "Received messages by what was done with them")
        List<MensajesPorResultadoResponse> entrantes,
        @Schema(description = "What went wrong, counted from the timelines") LoQueSalioMalResponse loQueSalioMal) {
}
