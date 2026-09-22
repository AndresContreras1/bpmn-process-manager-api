package com.facimus.procesos.gestion.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "The store to share a process with, identified by its tax id")
public record CompartirProcesoRequest(
        @Schema(description = "Colombian tax id (NIT) of the other store", example = "900123456-1")
        @NotBlank(message = "El NIT de la empresa es obligatorio.")
        @Size(max = 20, message = "El NIT no puede superar 20 caracteres.") String nit) {
}
