package com.facimus.procesos.modelado.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "An activity (task) inside a lane")
public record ActividadRequest(
        @Schema(description = "Name, unique among the flow nodes of the process", example = "Pick and pack items")
        @NotBlank(message = "El nombre es obligatorio.") String nombre,
        @Schema(example = "Collect the items and prepare the package.") String descripcion,
        @Schema(description = "Horizontal position on the diagram canvas", example = "580") int posicionX,
        @Schema(description = "Vertical position on the diagram canvas", example = "200") int posicionY) {
}
