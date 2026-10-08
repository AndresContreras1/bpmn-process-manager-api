package com.facimus.procesos.gestion.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "A new password, with the token of the recovery link")
public record NuevaClaveRequest(
        @Schema(example = "gJ0o0v5pX2m1Sx0c3yq5k8m7Tn4b6Wd9Qe1Rf2Uh3Zs")
        @NotBlank(message = "El token del enlace es obligatorio.")
        @Size(max = 100, message = "El token del enlace no puede superar 100 caracteres.") String token,
        @Schema(example = "mi-clave-nueva", format = "password")
        @NotBlank(message = "La contraseña nueva es obligatoria.")
        @Size(min = 6, message = "La contraseña debe tener al menos 6 caracteres.")
        @Size(max = 72, message = "La contraseña no puede superar 72 caracteres.") String nueva) {
}
