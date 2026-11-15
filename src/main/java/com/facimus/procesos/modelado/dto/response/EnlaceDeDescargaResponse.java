package com.facimus.procesos.modelado.dto.response;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

/** PR 61: un enlace para bajar una version, que vale solo unos minutos y no necesita sesion. */
@Schema(description = "A signed link to download one version; it works without a session until it expires")
public record EnlaceDeDescargaResponse(
        @Schema(example = "/api/v1/descargas/documentos/12/2?empresa=1&vence=1793872800&firma=Xy3...")
        String url,
        @Schema(example = "2026-11-12T09:35:00Z") Instant venceEn) {
}
