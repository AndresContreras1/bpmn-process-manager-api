package com.facimus.procesos.modelado.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "New name and content of a message flow; its pools cannot change")
public record EditarMensajeRequest(
        @Schema(example = "Payment authorization request")
        @NotBlank(message = "El nombre es obligatorio.")
        @Size(max = 120, message = "El nombre no puede superar 120 caracteres.") String nombre,
        @Schema(example = "Order total and tokenized card.")
        @NotBlank(message = "El contenido es obligatorio.")
        @Size(max = 2000, message = "El contenido no puede superar 2000 caracteres.") String contenido) {
}
