package com.facimus.procesos.gestion.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "A new name for a passkey")
public record EtiquetaPasskeyRequest(
        @Schema(example = "Phone")
        @NotBlank(message = "El nombre de la passkey es obligatorio.")
        @Size(max = 64, message = "El nombre de la passkey no puede superar 64 caracteres.") String etiqueta) {
}
