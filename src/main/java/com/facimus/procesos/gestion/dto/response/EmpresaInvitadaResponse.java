package com.facimus.procesos.gestion.dto.response;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A store that can read a process in read-only mode")
public record EmpresaInvitadaResponse(
        @Schema(example = "1") Long empresaId,
        @Schema(example = "Demo Store") String nombre,
        @Schema(description = "Colombian tax id (NIT)", example = "900123456-1") String nit,
        @Schema(example = "2026-09-22T09:30:00") LocalDateTime fechaCompartido) {
}
