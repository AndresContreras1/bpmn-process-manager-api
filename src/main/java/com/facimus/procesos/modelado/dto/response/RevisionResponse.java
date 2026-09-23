package com.facimus.procesos.modelado.dto.response;

import java.time.LocalDateTime;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "What the reviewer said about a diagram. It is advice, not a rule the API enforces")
public record RevisionResponse(
        @Schema(example = "1") Long procesoId,
        @Schema(example = "The process covers the sale but never says what happens when the payment is rejected")
        String resumen,
        @Schema(description = "Empty when the reviewer found nothing worth changing") List<HallazgoResponse> hallazgos,
        @Schema(description = "True when the diagram had not changed and the previous review was returned",
                example = "false") boolean reutilizada,
        @Schema(example = "2026-09-22T15:00:00") LocalDateTime fecha) {
}
