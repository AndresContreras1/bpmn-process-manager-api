package com.facimus.procesos.modelado.dto.request;

import com.facimus.procesos.modelado.model.TipoParticipante;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "New name and participant type of a pool")
public record EditarPoolRequest(
        @Schema(example = "Payment gateway")
        @NotBlank(message = "El nombre es obligatorio.")
        @Size(max = 120, message = "El nombre no puede superar 120 caracteres.") String nombre,
        @Schema(example = "SISTEMA_EXTERNO")
        @NotNull(message = "El tipo de participante es obligatorio.") TipoParticipante tipoParticipante,
        @Schema(description = "Version read with the last GET; if someone saved a change since, the edit answers 409",
                example = "0")
        @NotNull(message = "La versión es obligatoria.") Long version) {
}
