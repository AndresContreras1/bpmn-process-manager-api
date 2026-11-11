package com.facimus.procesos.modelado.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

/** PR 56: el subproceso donde va un nodo. */
public record ContenedorRequest(
        @Schema(description = "Expanded sub-process that will contain the node; empty takes it to the main flow",
                example = "12") Long padreId) {
}
