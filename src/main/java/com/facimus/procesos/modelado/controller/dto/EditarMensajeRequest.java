package com.facimus.procesos.modelado.controller.dto;

import jakarta.validation.constraints.NotBlank;

public record EditarMensajeRequest(
        @NotBlank(message = "El nombre es obligatorio.") String nombre,
        @NotBlank(message = "El contenido es obligatorio.") String contenido) {
}
