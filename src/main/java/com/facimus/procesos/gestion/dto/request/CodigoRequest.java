package com.facimus.procesos.gestion.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "A code of the second factor: the 6 digits of the authenticator app or one recovery code")
public record CodigoRequest(
        @Schema(example = "492039")
        @NotBlank(message = "El código es obligatorio.")
        @Size(max = 20, message = "El código no puede superar 20 caracteres.") String codigo) {
}
