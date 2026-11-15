package com.facimus.procesos.modelado.dto.response;

import java.time.LocalDateTime;

import com.facimus.procesos.modelado.model.Decision;

import io.swagger.v3.oas.annotations.media.Schema;

/** PR 64: lo que decidio un aprobador, con su texto ya saneado. */
@Schema(description = "What one approver decided")
public record DecisionResponse(
        @Schema(example = "9") Long id,
        PersonaResponse aprobador,
        @Schema(description = "APROBADA, CAMBIOS_PEDIDOS or RECHAZADA", example = "APROBADA") Decision decision,
        @Schema(example = "Looks good.") String markdown,
        @Schema(description = "The Markdown as HTML, through the same whitelist as comments",
                example = "<p>Looks good.</p>") String html,
        @Schema(example = "2026-11-12T11:00:00") LocalDateTime fecha) {
}
