package com.facimus.procesos.gestion.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A process that another store shares with the caller, in read-only mode")
public record ProcesoRecibidoResponse(
        ProcesoResponse proceso,
        @Schema(description = "The store that owns the process", example = "2") Long empresaPropietariaId,
        @Schema(example = "Acme Store") String empresaPropietariaNombre) {
}
