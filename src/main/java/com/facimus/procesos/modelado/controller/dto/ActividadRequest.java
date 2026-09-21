package com.facimus.procesos.modelado.controller.dto;

import jakarta.validation.constraints.NotBlank;

public record ActividadRequest(
        @NotBlank(message = "El nombre es obligatorio.") String nombre,
        String descripcion,
        int posicionX,
        int posicionY) {
}
