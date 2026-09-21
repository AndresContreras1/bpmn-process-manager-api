package com.facimus.procesos.modelado.controller.dto;

import com.facimus.procesos.modelado.model.TipoParticipante;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "New name and participant type of a pool")
public record EditarPoolRequest(
        @Schema(example = "Payment gateway")
        @NotBlank(message = "El nombre es obligatorio.") String nombre,
        @Schema(example = "SISTEMA_EXTERNO")
        @NotNull(message = "El tipo de participante es obligatorio.") TipoParticipante tipoParticipante) {
}
