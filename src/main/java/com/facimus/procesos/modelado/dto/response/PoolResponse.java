package com.facimus.procesos.modelado.dto.response;

import java.time.LocalDateTime;

import com.facimus.procesos.modelado.model.TipoParticipante;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A participant of the process")
public record PoolResponse(
        @Schema(example = "3") Long id,
        @Schema(example = "Payment gateway") String nombre,
        @Schema(example = "SISTEMA_EXTERNO") TipoParticipante tipoParticipante,
        @Schema(example = "true") boolean cajaNegra,
        @Schema(description = "Position in the process; the store's own pool is 0", example = "2") int orden,
        @Schema(example = "1") Long procesoId,
        @Schema(description = "Send it back when editing; it goes up with every saved change", example = "0")
        Long version,
        @Schema(description = "Id of the user who created it; empty when the system did, like the store registration",
                example = "2") Long creadoPor,
        @Schema(example = "2026-09-21T15:00:00") LocalDateTime fechaCreacion,
        @Schema(description = "Id of the user who saved the last change", example = "5") Long modificadoPor,
        @Schema(example = "2026-09-21T15:30:00") LocalDateTime fechaModificacion) {
}
