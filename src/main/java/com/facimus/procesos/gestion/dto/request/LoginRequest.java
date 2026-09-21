package com.facimus.procesos.gestion.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Login credentials; the example is the demo store administrator of the dev profile")
public record LoginRequest(
        @Schema(example = "admin@demo.com")
        @NotBlank(message = "El correo es obligatorio.")
        @Size(max = 254, message = "El correo no puede superar 254 caracteres.") String email,
        @Schema(example = "admin123", format = "password")
        @NotBlank(message = "La contrasena es obligatoria.")
        @Size(max = 72, message = "La contrasena no puede superar 72 caracteres.") String password) {
}
