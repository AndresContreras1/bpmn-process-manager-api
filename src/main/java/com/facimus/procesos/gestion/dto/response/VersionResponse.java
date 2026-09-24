package com.facimus.procesos.gestion.dto.response;

import java.time.LocalDateTime;

import com.facimus.procesos.gestion.model.EstadoVersion;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A published version of a process: the diagram frozen the day it was published")
public record VersionResponse(
        @Schema(example = "4") Long id,
        @Schema(example = "1") Long procesoId,
        @Schema(description = "Starts at 1 and goes up with every publication", example = "2") int numero,
        @Schema(example = "VIGENTE") EstadoVersion estado,
        @Schema(example = "2026-09-23T11:05:00") LocalDateTime fechaPublicacion,
        @Schema(description = "Id of the user who published it; empty when the system did", example = "2")
        Long publicadoPor,
        @Schema(description = "SHA-256 of the canonical diagram: two versions with the same fingerprint would draw "
                + "the same picture", example = "9f2c...") String huella) {
}
