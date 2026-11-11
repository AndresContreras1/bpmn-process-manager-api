package com.facimus.procesos.modelado.dto.response;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

/** PR 58: quien esta editando un proceso, visto por quien pregunta. */
public record BloqueoResponse(
        @Schema(description = "Nobody is editing it") boolean libre,
        @Schema(description = "It is whoever asks") boolean mio,
        @Schema(example = "2") Long usuarioId,
        @Schema(example = "Ana Ruiz") String nombre,
        @Schema(example = "2026-11-11T09:30:00") LocalDateTime desde,
        @Schema(description = "When it is lost without a heartbeat", example = "2026-11-11T09:31:00")
        LocalDateTime vence) {
}
