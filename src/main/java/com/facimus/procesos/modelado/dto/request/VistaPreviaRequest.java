package com.facimus.procesos.modelado.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** PR 62: un texto en Markdown para ver como quedaria, sin guardarlo. */
@Schema(description = "Markdown to preview, without saving it")
public record VistaPreviaRequest(
        @Schema(example = "## Packing\n\n- Wrap the order") @NotNull(message = "El texto es obligatorio.")
        @Size(max = 100_000, message = "La documentación no puede superar 100 000 caracteres.") String markdown) {
}
