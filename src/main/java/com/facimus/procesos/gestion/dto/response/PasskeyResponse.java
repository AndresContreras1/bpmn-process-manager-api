package com.facimus.procesos.gestion.dto.response;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A passkey of the signed-in user. The server keeps only its public key")
public record PasskeyResponse(
        @Schema(example = "4") Long id,
        @Schema(description = "The name the user gave it", example = "Work laptop") String etiqueta,
        @Schema(example = "2026-11-09T15:00:00") LocalDateTime creadaEn,
        @Schema(description = "Empty until it is used to sign in", example = "2026-11-10T08:30:00")
        LocalDateTime ultimoUsoEn,
        @Schema(description = "True when the passkey is backed up by its provider (iCloud Keychain, Google Password "
                + "Manager...) and so works on the user's other devices", example = "true") boolean sincronizada) {
}
