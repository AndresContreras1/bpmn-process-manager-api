package com.facimus.procesos.gestion.controller.dto;

import com.facimus.procesos.gestion.model.EstadoProceso;

import jakarta.validation.constraints.NotNull;

public record CambiarEstadoProcesoRequest(
        @NotNull(message = "El estado es obligatorio.") EstadoProceso estado) {
}