package com.facimus.procesos.gestion.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A process that uses a role")
public record ProcesoDelRolResponse(
        @Schema(example = "1") Long id,
        @Schema(example = "Order fulfillment") String nombre) {
}
