package com.facimus.procesos.gestion.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "The result of recomputing the store's chain from its first entry")
public record VerificacionDeBitacoraResponse(
        @Schema(description = "True when every entry still matches its hash and follows the previous one",
                example = "true") boolean integra,
        @Schema(description = "Entries checked", example = "128") long registros,
        @Schema(description = "The first entry where the chain breaks; empty when it does not", example = "57")
        Long rotaEn,
        @Schema(description = "Why it breaks there", example = "El hash no coincide con lo que dice el registro.")
        String motivo) {
}
