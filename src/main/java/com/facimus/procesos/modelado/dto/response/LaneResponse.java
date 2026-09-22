package com.facimus.procesos.modelado.dto.response;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A lane of a pool")
public record LaneResponse(
        @Schema(example = "4") Long id,
        @Schema(example = "Warehouse") String nombre,
        @Schema(description = "Position inside the pool, starting at 0", example = "1") int orden,
        @Schema(example = "1") Long poolId,
        @Schema(example = "2") Long rolProcesoId,
        @Schema(example = "Warehouse") String rolProcesoNombre,
        @Schema(description = "Send it back when editing; it goes up with every saved change", example = "0")
        Long version,
        @Schema(description = "Id of the user who created it; empty when the system did, like the store registration",
                example = "2") Long creadoPor,
        @Schema(example = "2026-09-21T15:00:00") LocalDateTime fechaCreacion,
        @Schema(description = "Id of the user who saved the last change", example = "5") Long modificadoPor,
        @Schema(example = "2026-09-21T15:30:00") LocalDateTime fechaModificacion) {
}
