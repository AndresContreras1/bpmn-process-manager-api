package com.facimus.procesos.gestion.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Login credentials; the example is the demo store administrator of the dev profile")
public record LoginRequest(
        @Schema(example = "admin@demo.com")
        @NotBlank(message = "El correo es obligatorio.") String email,
        @Schema(example = "admin123", format = "password")
        @NotBlank(message = "La contrasena es obligatoria.") String password) {
}
