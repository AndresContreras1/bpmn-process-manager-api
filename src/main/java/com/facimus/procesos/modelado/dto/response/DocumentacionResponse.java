package com.facimus.procesos.modelado.dto.response;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

/** PR 62: la documentacion de un proceso o de uno de sus elementos, con su HTML ya saneado. */
@Schema(description = "Documentation of a process or element: the Markdown and the cleaned HTML to show")
public record DocumentacionResponse(
        @Schema(description = "The element it documents; empty for the whole process", example = "Activity_0b3k9xq")
        String bpmnId,
        @Schema(example = "Wrap the order **twice**.") String markdown,
        @Schema(description = "The Markdown as HTML, through a whitelist: what is safe to show",
                example = "<p>Wrap the order <strong>twice</strong>.</p>") String html,
        @Schema(description = "Send it back when editing; empty when there is none", example = "0") Long version,
        @Schema(example = "2") Long modificadoPor,
        @Schema(example = "2026-11-12T10:00:00") LocalDateTime fechaModificacion) {
}
