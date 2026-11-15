package com.facimus.procesos.ejecucion.dto.response;

import java.util.List;

import com.facimus.procesos.common.Default;

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
        @Schema(description = "What went wrong, counted from the timelines") LoQueSalioMalResponse loQueSalioMal,
        @Schema(description = "How deadlines were kept, by process, activity and person, and for whole cases")
        CumplimientoResponse cumplimiento) {

    /** El de antes del PR 71, sin cumplimiento. */
    @SuppressWarnings("java:S107") // las partes del tablero, por su nombre
    public TableroResponse(Long procesoId, long casos, List<CasosPorEstadoResponse> casosPorEstado,
            CicloDeCasoResponse ciclo, List<TareasPorRolResponse> tareasPorRol, List<MensajesPorEstadoResponse> salientes,
            List<MensajesPorResultadoResponse> entrantes, LoQueSalioMalResponse loQueSalioMal) {
        this(procesoId, casos, casosPorEstado, ciclo, tareasPorRol, salientes, entrantes, loQueSalioMal, null);
    }

    @Default
    public TableroResponse {
        // El constructor completo es el que usan Jackson y el servicio
    }
}
