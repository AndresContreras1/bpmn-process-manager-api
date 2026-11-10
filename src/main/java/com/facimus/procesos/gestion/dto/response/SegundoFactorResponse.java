package com.facimus.procesos.gestion.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "The second factor of the signed-in user")
public record SegundoFactorResponse(
        @Schema(description = "True when the authenticator app is confirmed and the login asks for its code",
                example = "true") boolean totp,
        @Schema(description = "Passkeys of the user; with one, the login asks for the second factor too",
                example = "1") long passkeys,
        @Schema(description = "Recovery codes not used yet", example = "10") long codigosRestantes) {
}
