package com.facimus.procesos.gestion.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "The refresh token received at login or at the last renewal")
public record RenovarTokenRequest(
        @Schema(example = "gJ0o0v5pX2m1Sx0c3yq5k8m7Tn4b6Wd9Qe1Rf2Uh3Zs")
        @NotBlank(message = "El refresh token es obligatorio.")
        @Size(max = 100, message = "El refresh token no puede superar 100 caracteres.") String refreshToken) {
}
