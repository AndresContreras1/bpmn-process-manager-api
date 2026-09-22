package com.facimus.procesos.modelado.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "A message flow between two different pools of the process")
public record MensajeRequest(
        @Schema(example = "Payment authorization request")
        @NotBlank(message = "El nombre es obligatorio.")
        @Size(max = 120, message = "El nombre no puede superar 120 caracteres.") String nombre,
        @Schema(example = "Order total and tokenized card.")
        @NotBlank(message = "El contenido es obligatorio.")
        @Size(max = 2000, message = "El contenido no puede superar 2000 caracteres.") String contenido,
        @Schema(description = "Sending pool, a participant of the same process", example = "1")
        @NotNull(message = "El pool de origen es obligatorio.") Long poolOrigenId,
        @Schema(description = "Receiving pool, different from the sender", example = "3")
        @NotNull(message = "El pool de destino es obligatorio.") Long poolDestinoId) {
}
