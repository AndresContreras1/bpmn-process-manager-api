package com.facimus.procesos.modelado.dto.response;

import java.time.LocalDateTime;
import java.util.List;

import com.facimus.procesos.modelado.model.EstadoHilo;

import io.swagger.v3.oas.annotations.media.Schema;

/** PR 63: un comentario, con su HTML ya saneado y los nombres de quien escribe y de quien menciona. */
@Schema(description = "A comment: the Markdown and the cleaned HTML to show. A deleted one keeps its place in its "
        + "thread with no text")
public record ComentarioResponse(
        @Schema(example = "12") Long id,
        @Schema(example = "7") Long procesoId,
        @Schema(description = "The first comment of its thread; empty if this is the first", example = "11")
        Long padreId,
        @Schema(description = "The element the thread is about; empty for the whole process",
                example = "Activity_0b3k9xq") String bpmnId,
        @Schema(description = "The published version the thread is about; empty for the draft", example = "2")
        Integer versionDelProceso,
        PersonaResponse autor,
        @Schema(example = "Who checks the **address** before packing?") String markdown,
        @Schema(description = "The Markdown as HTML, through a whitelist: what is safe to show",
                example = "<p>Who checks the <strong>address</strong> before packing?</p>") String html,
        List<PersonaResponse> menciones,
        @Schema(description = "ABIERTO or RESUELTO; only the first comment of a thread has it", example = "ABIERTO")
        EstadoHilo estado,
        @Schema(description = "Who resolved the thread") PersonaResponse resueltoPor,
        @Schema(example = "2026-11-12T11:00:00") LocalDateTime resueltoEn,
        @Schema(description = "When its text last changed; empty if never", example = "2026-11-12T10:05:00")
        LocalDateTime editadoEn,
        @Schema(description = "Deleted: it has no text, and stays only while its thread has replies",
                example = "false") boolean eliminado,
        @Schema(example = "2026-11-12T10:00:00") LocalDateTime fechaCreacion,
        @Schema(description = "Send it back when editing", example = "0") Long version) {
}
