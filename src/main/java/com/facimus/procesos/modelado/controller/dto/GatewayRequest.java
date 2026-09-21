package com.facimus.procesos.modelado.controller.dto;

import com.facimus.procesos.modelado.model.TipoGateway;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record GatewayRequest(
        @NotBlank(message = "El nombre es obligatorio.") String nombre,
        @NotNull(message = "El tipo de gateway es obligatorio.") TipoGateway tipoGateway,
        int posicionX,
        int posicionY) {
}
