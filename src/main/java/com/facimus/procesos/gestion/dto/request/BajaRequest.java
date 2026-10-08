package com.facimus.procesos.gestion.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Closing the store: its name, written again, so a stray click does not start it")
public record BajaRequest(
        @Schema(description = "The store's name, as it is", example = "Acme Store")
        @NotBlank(message = "Escribe el nombre de la tienda para confirmar la baja.")
        @Size(max = 120, message = "El nombre de la tienda no supera 120 caracteres.") String confirmacion) {
}
