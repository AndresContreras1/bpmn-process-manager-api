package com.facimus.procesos.modelado.controller.dto;

import com.facimus.procesos.modelado.model.TipoParticipante;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record EditarPoolRequest(
        @NotBlank(message = "El nombre es obligatorio.") String nombre,
        @NotNull(message = "El tipo de participante es obligatorio.") TipoParticipante tipoParticipante) {
}
