package com.facimus.procesos.modelado.dto.request;

import com.facimus.procesos.modelado.model.UnidadDePlazo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** PR 71: el plazo de una tarea de usuario o del caso entero. Sin cantidad ni unidad, el nodo se queda sin plazo. */
@Schema(description = "The deadline of a user task, or of the whole case on a start event; without cantidad and "
        + "unidad the node has none")
public record PlazoRequest(
        @Schema(description = "1 to 8784 hours or 1 to 366 business days", example = "2") Integer cantidad,
        @Schema(description = "HORAS (wall-clock hours) or DIAS_HABILES (business days of the organization's "
                + "calendar)", example = "DIAS_HABILES") UnidadDePlazo unidad,
        @Schema(description = "Version of the node read with the last GET; if someone saved a change since, it "
                + "answers 409", example = "0")
        @NotNull(message = "La versión es obligatoria.") Long version) {
}
