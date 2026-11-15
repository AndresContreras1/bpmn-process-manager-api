package com.facimus.procesos.ejecucion.dto.response;

import java.time.LocalDateTime;

import com.facimus.procesos.ejecucion.model.ClaseTemporizador;
import com.facimus.procesos.ejecucion.model.EstadoTemporizador;
import com.facimus.procesos.ejecucion.model.ModoCaso;
import com.facimus.procesos.modelado.model.TipoTemporizador;

import io.swagger.v3.oas.annotations.media.Schema;

/** PR 68: un temporizador armado de un caso, o un inicio temporizado de un proceso. */
@Schema(description = "A timer armed by a case, or the timer start of a process")
public record TemporizadorResponse(
        @Schema(example = "31") Long id,
        @Schema(description = "Case it belongs to; empty for a timer start, which opens cases", example = "42")
        Long casoId,
        @Schema(description = "Id of the timer event inside the version", example = "18") Long nodoId,
        @Schema(example = "Wait two business days") String nodoNombre,
        @Schema(description = "INICIO (timer start), INTERMEDIO (a wait) or BORDE (attached to an activity)",
                example = "INTERMEDIO") ClaseTemporizador clase,
        @Schema(description = "PRODUCCION counts wall-clock time with the business calendar; PRUEBA counts ticks",
                example = "PRODUCCION") ModoCaso modo,
        @Schema(example = "DURACION_HABIL") TipoTemporizador tipoTemporizador,
        @Schema(description = "ISO 8601 duration, date or cycle, or a cron expression", example = "P2D")
        String expresion,
        @Schema(description = "For a boundary timer, whether it interrupts the activity", example = "true")
        Boolean interrumpe,
        @Schema(description = "When it is due, in UTC; for a PRODUCCION timer", example = "2026-11-17T20:00:00")
        LocalDateTime venceEn,
        @Schema(description = "Store clock tick it is due at; for a PRUEBA timer", example = "21") Integer venceTick,
        @Schema(description = "For a cycle, how many times it fires; empty when it never ends", example = "3")
        Integer repeticiones,
        @Schema(description = "How many times it has fired", example = "0") int disparos,
        @Schema(example = "PROGRAMADO") EstadoTemporizador estado,
        @Schema(description = "When it last fired, in UTC", example = "2026-11-17T20:00:03") LocalDateTime disparadoEn,
        @Schema(description = "Why it could not fire, when it failed") String ultimoError) {
}
