package com.facimus.procesos.modelado.dto.response;

import java.time.LocalDateTime;

import com.facimus.procesos.modelado.model.EstadoDocumento;

import io.swagger.v3.oas.annotations.media.Schema;

/** PR 61: un documento con lo que se sabe de su ultima version. */
@Schema(description = "A document attached to a process or to one of its elements, with its latest version")
public record DocumentoResponse(
        @Schema(example = "12") Long id,
        @Schema(example = "1") Long procesoId,
        @Schema(description = "The element it is attached to; empty for the whole process",
                example = "Activity_0b3k9xq") String bpmnId,
        @Schema(description = "File name of the latest version, cleaned, with the extension of its real type",
                example = "packing-instructions.pdf") String nombre,
        @Schema(description = "Number of the latest version", example = "2") int versionActual,
        @Schema(description = "PENDIENTE while it is being checked, DISPONIBLE to download, BLOQUEADO with a reason",
                example = "DISPONIBLE") EstadoDocumento estado,
        @Schema(description = "Why the latest version was blocked", example = "Win.Test.EICAR_HDB-1") String motivo,
        @Schema(description = "Whether an antivirus checked the latest version", example = "true") boolean revisado,
        @Schema(description = "Type detected from the content", example = "application/pdf") String tipo,
        @Schema(description = "Size of the latest version in bytes", example = "48213") long tamano,
        @Schema(description = "Send it back when editing", example = "1") Long version,
        @Schema(example = "2026-11-12T09:30:00") LocalDateTime fechaModificacion) {
}
