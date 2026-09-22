package com.facimus.procesos.gestion.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Tokens of the session and the profile of the user")
public record LoginResponse(
        @Schema(description = "Signed JWT to send as Authorization: Bearer <token>",
                example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbkBkZW1vLmNvbSJ9.signature") String accessToken,
        @Schema(example = "Bearer") String tokenType,
        @Schema(description = "Seconds until the access token expires", example = "900") long expiresIn,
        @Schema(description = "Opaque token that renews the session once at POST /api/v1/auth/refresh. Keep it "
                + "secret: sending it twice closes the session",
                example = "gJ0o0v5pX2m1Sx0c3yq5k8m7Tn4b6Wd9Qe1Rf2Uh3Zs") String refreshToken,
        UsuarioResponse usuario) {
}
