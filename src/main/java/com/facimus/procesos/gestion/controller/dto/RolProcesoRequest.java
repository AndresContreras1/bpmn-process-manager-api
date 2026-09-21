package com.facimus.procesos.gestion.controller.dto;

import jakarta.validation.constraints.NotBlank;

public record RolProcesoRequest(
        @NotBlank(message = "El nombre del rol es obligatorio.") String nombre,
        String descripcion) {
}
