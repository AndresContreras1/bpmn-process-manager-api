package com.facimus.procesos.modelado.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record MensajeRequest(
        @NotBlank(message = "El nombre es obligatorio.") String nombre,
        @NotBlank(message = "El contenido es obligatorio.") String contenido,
        @NotNull(message = "El pool de origen es obligatorio.") Long poolOrigenId,
        @NotNull(message = "El pool de destino es obligatorio.") Long poolDestinoId) {
}
