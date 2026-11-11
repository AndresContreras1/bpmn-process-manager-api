package com.facimus.procesos.gestion.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "A folder of the process repository")
public record CarpetaRequest(
        @Schema(description = "Name, unique among its sisters", example = "Fulfillment")
        @NotBlank(message = "El nombre es obligatorio.")
        @Size(max = 120, message = "El nombre no puede superar 120 caracteres.") String nombre,
        @Schema(description = "The parent folder; empty for one at the root", example = "1") Long padreId) {
}
