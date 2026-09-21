package com.facimus.procesos.gestion.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Access token and the profile of the user who logged in")
public record LoginResponse(
        @Schema(description = "Signed JWT to send as Authorization: Bearer <token>",
                example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbkBkZW1vLmNvbSJ9.signature") String accessToken,
        @Schema(example = "Bearer") String tokenType,
        @Schema(description = "Seconds until the token expires", example = "1800") long expiresIn,
        UsuarioResponse usuario) {
}
