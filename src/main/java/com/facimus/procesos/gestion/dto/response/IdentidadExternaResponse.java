package com.facimus.procesos.gestion.dto.response;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "An account of Google or Microsoft linked to the signed-in user")
public record IdentidadExternaResponse(
        @Schema(example = "3") Long id,
        @Schema(description = "Who keeps the account: google or microsoft", example = "google") String proveedor,
        @Schema(description = "The e-mail the provider gave when it was linked", example = "ana@acme.com") String email,
        @Schema(example = "2026-11-09T15:00:00") LocalDateTime vinculadaEn) {
}
