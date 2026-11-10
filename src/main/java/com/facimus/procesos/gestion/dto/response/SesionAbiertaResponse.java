package com.facimus.procesos.gestion.dto.response;

import java.time.LocalDateTime;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * F14: una sesion abierta de la persona, para verla y cerrarla desde su cuenta: cuando entro, cuando se renovo por
 * ultima vez, con que factores, y si es la de quien pregunta.
 */
@Schema(description = "An open session of the caller: when it started, when it was last renewed, the factors it was "
        + "opened with, and whether it is the one asking. Dates in UTC")
public record SesionAbiertaResponse(
        @Schema(example = "42") Long id,
        @Schema(example = "2026-11-10T13:05:00") LocalDateTime inicio,
        @Schema(description = "The last login or renewal", example = "2026-11-10T14:20:00") LocalDateTime ultimaActividad,
        @Schema(example = "[\"PASSWORD\", \"TOTP\"]") List<String> factores,
        @Schema(description = "True for the session of the cookies that asked", example = "true") boolean actual) {
}
