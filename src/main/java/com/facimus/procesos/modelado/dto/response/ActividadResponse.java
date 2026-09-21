package com.facimus.procesos.modelado.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "An activity (task) of the process")
public record ActividadResponse(
        @Schema(example = "10") Long id,
        @Schema(example = "Pick and pack items") String nombre,
        @Schema(example = "Collect the items and prepare the package.") String descripcion,
        @Schema(example = "580") int posicionX,
        @Schema(example = "200") int posicionY,
        @Schema(example = "4") Long laneId) {
}
