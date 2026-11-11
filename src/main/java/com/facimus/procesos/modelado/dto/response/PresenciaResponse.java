package com.facimus.procesos.modelado.dto.response;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

/** PR 58: alguien que esta mirando un proceso. */
public record PresenciaResponse(
        @Schema(example = "2") Long usuarioId,
        @Schema(example = "Ana Ruiz") String nombre,
        @Schema(example = "2026-11-11T09:30:00") LocalDateTime desde) {
}
