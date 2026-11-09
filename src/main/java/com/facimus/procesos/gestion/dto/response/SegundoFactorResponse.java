package com.facimus.procesos.gestion.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "The second factor of the signed-in user")
public record SegundoFactorResponse(
        @Schema(description = "True when the authenticator app is confirmed and the login asks for its code",
                example = "true") boolean totp,
        @Schema(description = "Recovery codes not used yet", example = "10") long codigosRestantes) {
}
