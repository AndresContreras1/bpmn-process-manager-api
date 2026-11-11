package com.facimus.procesos.gestion.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A folder of the process repository")
public record CarpetaResponse(
        @Schema(example = "1") Long id,
        @Schema(example = "Fulfillment") String nombre,
        @Schema(description = "The parent folder; empty at the root", example = "2") Long padreId,
        @Schema(description = "Active processes directly in it", example = "3") long procesos,
        @Schema(description = "Send it back when editing", example = "0") Long version) {
}
