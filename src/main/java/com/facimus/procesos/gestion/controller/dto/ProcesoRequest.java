package com.facimus.procesos.gestion.controller.dto;

import jakarta.validation.constraints.NotBlank;

public record ProcesoRequest(
        @NotBlank(message = "El nombre es obligatorio.") String nombre,
        @NotBlank(message = "La descripcion es obligatoria.") String descripcion,
        @NotBlank(message = "La categoria es obligatoria.") String categoria) {
}
