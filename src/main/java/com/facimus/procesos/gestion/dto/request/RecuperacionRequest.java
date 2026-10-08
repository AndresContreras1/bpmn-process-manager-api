package com.facimus.procesos.gestion.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Who forgot their password")
public record RecuperacionRequest(
        @Schema(example = "ana@acme.com")
        @NotBlank(message = "El correo es obligatorio.")
        @Size(max = 254, message = "El correo no puede superar 254 caracteres.") String email) {
}
