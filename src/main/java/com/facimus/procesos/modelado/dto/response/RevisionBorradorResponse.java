package com.facimus.procesos.modelado.dto.response;

import java.time.LocalDateTime;

import com.facimus.procesos.modelado.model.MotivoDeRevision;

import io.swagger.v3.oas.annotations.media.Schema;

/** PR 40: un punto de guardado del borrador. */
public record RevisionBorradorResponse(
        @Schema(example = "12") Long id,
        @Schema(description = "AUTOGUARDADO, CIERRE or ANTES_DE_RESTAURAR", example = "AUTOGUARDADO")
        MotivoDeRevision motivo,
        @Schema(description = "SHA-256 of the BPMN file at that point; restoring gives this same value") String huella,
        @Schema(description = "Id of the user who saved it", example = "2") Long usuarioId,
        @Schema(example = "2026-11-11T09:30:00") LocalDateTime fecha) {
}
