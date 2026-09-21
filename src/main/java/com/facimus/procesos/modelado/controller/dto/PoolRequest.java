package com.facimus.procesos.modelado.controller.dto;

import com.facimus.procesos.modelado.model.TipoParticipante;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "A participant of the process")
public record PoolRequest(
        @Schema(example = "Payment gateway")
        @NotBlank(message = "El nombre es obligatorio.") String nombre,
        @Schema(example = "SISTEMA_EXTERNO")
        @NotNull(message = "El tipo de participante es obligatorio.") TipoParticipante tipoParticipante,
        @Schema(description = "true when the store does not model the participant's internal flow", example = "true")
        boolean cajaNegra) {
}
