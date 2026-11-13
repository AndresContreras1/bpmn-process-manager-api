package com.facimus.procesos.modelado.dto.response;

import java.time.LocalDateTime;

import com.facimus.procesos.modelado.model.EstadoManual;

import io.swagger.v3.oas.annotations.media.Schema;

/** PR 62: un manual pedido y en que va. */
@Schema(description = "A process manual: which version, in which language, and whether it is ready")
public record ManualResponse(
        @Schema(example = "5") Long id,
        @Schema(example = "1") Long procesoId,
        @Schema(description = "The published version it describes", example = "3") int version,
        @Schema(example = "es-CO") String idioma,
        @Schema(description = "PENDIENTE while the queue builds it, DISPONIBLE, or FALLIDO with a reason",
                example = "DISPONIBLE") EstadoManual estado,
        @Schema(example = "La versión 3 no tiene su dibujo.") String motivo,
        @Schema(example = "2026-11-12T10:00:00") LocalDateTime pedidoEn,
        @Schema(example = "2026-11-12T10:00:04") LocalDateTime listoEn) {
}
