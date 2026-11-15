package com.facimus.procesos.gestion.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "A day the organization makes a public holiday, or whose holiday it renames")
public record FestivoRequest(
        @Schema(description = "Name of the holiday", example = "King's Birthday")
        @NotBlank(message = "El nombre del festivo es obligatorio.")
        @Size(max = 120, message = "El nombre del festivo no puede superar 120 caracteres.") String nombre) {
}
