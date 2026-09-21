package com.facimus.procesos.modelado.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A lane of a pool")
public record LaneResponse(
        @Schema(example = "4") Long id,
        @Schema(example = "Warehouse") String nombre,
        @Schema(description = "Position inside the pool, starting at 0", example = "1") int orden,
        @Schema(example = "1") Long poolId,
        @Schema(example = "2") Long rolProcesoId,
        @Schema(example = "Warehouse") String rolProcesoNombre) {
}
