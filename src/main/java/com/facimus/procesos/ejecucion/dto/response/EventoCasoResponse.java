package com.facimus.procesos.ejecucion.dto.response;

import java.time.LocalDateTime;

import com.facimus.procesos.ejecucion.model.TipoEventoCaso;

import io.swagger.v3.oas.annotations.media.Schema;

/** Una linea de la bitacora de un caso: que paso, cuando y por que. */
@Schema(description = "One line of the case timeline")
public record EventoCasoResponse(
        @Schema(example = "120") Long id,
        @Schema(description = "Store clock tick when it happened", example = "0") int tick,
        @Schema(example = "2026-09-24T15:00:00") LocalDateTime fecha,
        @Schema(example = "GATEWAY_DECIDIO") TipoEventoCaso tipo,
        @Schema(example = "\"Payment approved?\" sigue por [\"Approved\"]") String detalle,
        @Schema(description = "User who caused it; empty when the engine did", example = "5") Long autorId) {
}
