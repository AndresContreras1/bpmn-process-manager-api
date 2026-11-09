package com.facimus.procesos.gestion.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Confirming who you are again, with the password of the signed-in user")
public record ReautenticacionRequest(
        @Schema(example = "every-order-on-time", format = "password")
        @NotBlank(message = "La contraseña es obligatoria.")
        @Size(max = 72, message = "La contraseña no puede superar 72 caracteres.") String password) {
}
