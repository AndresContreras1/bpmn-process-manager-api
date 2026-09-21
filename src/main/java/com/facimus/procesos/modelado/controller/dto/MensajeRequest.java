package com.facimus.procesos.modelado.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "A message flow between two different pools of the process")
public record MensajeRequest(
        @Schema(example = "Payment authorization request")
        @NotBlank(message = "El nombre es obligatorio.") String nombre,
        @Schema(example = "Order total and tokenized card.")
        @NotBlank(message = "El contenido es obligatorio.") String contenido,
        @Schema(description = "Sending pool", example = "1")
        @NotNull(message = "El pool de origen es obligatorio.") Long poolOrigenId,
        @Schema(description = "Receiving pool, different from the sender", example = "3")
        @NotNull(message = "El pool de destino es obligatorio.") Long poolDestinoId) {
}
