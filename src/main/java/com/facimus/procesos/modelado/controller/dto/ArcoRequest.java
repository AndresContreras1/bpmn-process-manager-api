package com.facimus.procesos.modelado.controller.dto;

import jakarta.validation.constraints.NotNull;

public record ArcoRequest(
        @NotNull(message = "El nodo de origen es obligatorio.") Long origenId,
        @NotNull(message = "El nodo de destino es obligatorio.") Long destinoId,
        String etiqueta,
        String condicion) {
}
