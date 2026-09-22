package com.facimus.procesos.modelado.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "New name and process role of a lane, with the version that was read")
public record EditarLaneRequest(
        @Schema(example = "Warehouse")
        @NotBlank(message = "El nombre es obligatorio.")
        @Size(max = 120, message = "El nombre no puede superar 120 caracteres.") String nombre,
        @Schema(description = "Process role of the same store", example = "2")
        @NotNull(message = "El rol de proceso es obligatorio.") Long rolProcesoId,
        @Schema(description = "Version read with the last GET; if someone saved a change since, the edit answers 409",
                example = "0")
        @NotNull(message = "La versión es obligatoria.") Long version) {
}
