package com.facimus.procesos.modelado.dto.response;

import java.time.LocalDateTime;

import com.facimus.procesos.modelado.model.EstadoDocumento;

import io.swagger.v3.oas.annotations.media.Schema;

/** PR 61: un archivo subido a un documento. */
@Schema(description = "One uploaded file of a document")
public record VersionDocumentoResponse(
        @Schema(example = "1") int numero,
        @Schema(example = "packing-instructions.pdf") String nombre,
        @Schema(example = "application/pdf") String tipo,
        @Schema(example = "48213") long tamano,
        @Schema(description = "SHA-256 of the file, in hex",
                example = "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08") String sha256,
        @Schema(example = "DISPONIBLE") EstadoDocumento estado,
        @Schema(example = "El archivo crece demasiado al abrirlo.") String motivo,
        @Schema(example = "true") boolean revisado,
        @Schema(description = "Id of the user who uploaded it", example = "2") Long subidoPor,
        @Schema(example = "2026-11-12T09:30:00") LocalDateTime fecha) {
}
