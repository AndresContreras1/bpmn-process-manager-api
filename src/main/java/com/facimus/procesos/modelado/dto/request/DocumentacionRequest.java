package com.facimus.procesos.modelado.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** PR 62: la documentacion de un proceso o de uno de sus elementos. */
@Schema(description = "Markdown documentation of a process, or of one of its elements")
public record DocumentacionRequest(
        @Schema(description = "bpmnId of the element; empty for the whole process", example = "Activity_0b3k9xq")
        String elemento,
        @Schema(description = "The text in Markdown; empty removes it", example = "Wrap the order **twice**.")
        @NotNull(message = "El texto es obligatorio, aunque sea vacío.")
        @Size(max = 100_000, message = "La documentación no puede superar 100 000 caracteres.") String markdown,
        @Schema(description = "Version read with the last GET; empty for a new one", example = "0") Long version) {
}
