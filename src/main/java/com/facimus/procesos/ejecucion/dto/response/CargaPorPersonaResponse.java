package com.facimus.procesos.ejecucion.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** PR 70: las tareas que esperan en manos de una persona. */
@Schema(description = "How many waiting tasks one person holds")
public record CargaPorPersonaResponse(
        @Schema(example = "5") Long usuarioId,
        @Schema(example = "Luis Gomez") String nombre,
        @Schema(example = "4") long tareas) {
}
