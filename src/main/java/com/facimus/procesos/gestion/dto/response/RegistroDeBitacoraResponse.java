package com.facimus.procesos.gestion.dto.response;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "One entry of the store's audit trail")
public record RegistroDeBitacoraResponse(
        @Schema(description = "Position in the store's chain, from 1", example = "42") long secuencia,
        @Schema(description = "When it happened, in UTC", example = "2026-11-09T15:00:00") LocalDateTime momento,
        @Schema(example = "USUARIO") String actorTipo,
        @Schema(description = "Id of the user; empty when the API did it", example = "2") Long actorId,
        @Schema(example = "SESION_INICIADA") String accion,
        @Schema(example = "USUARIO") String recurso,
        @Schema(example = "2") Long recursoId,
        @Schema(example = "true") boolean exito,
        @Schema(example = "203.0.113.7") String ip,
        @Schema(example = "Mozilla/5.0") String userAgent,
        @Schema(example = "3f1c...") String requestId,
        @Schema(example = "4bf92f3577b34da6a3ce929d0e0e4736") String traceId,
        @Schema(description = "What else the entry says, as JSON", example = "{\"factores\":\"PASSWORD,TOTP\"}")
        String detalle,
        @Schema(description = "SHA-256 of this entry together with the previous one") String hash) {
}
