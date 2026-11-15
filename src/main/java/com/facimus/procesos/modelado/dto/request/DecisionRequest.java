package com.facimus.procesos.modelado.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/** PR 64: lo que un aprobador dice al decidir; pedir cambios o rechazar siempre dice por que. */
@Schema(description = "An approver's decision on a review request")
public record DecisionRequest(
        @Schema(description = "Why, in Markdown: optional to approve, required to ask for changes or reject",
                example = "The fraud check has no timeout: add one.")
        @Size(max = 10_000, message = "El texto no puede superar 10 000 caracteres.") String markdown) {
}
