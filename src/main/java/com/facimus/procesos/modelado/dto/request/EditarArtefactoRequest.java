package com.facimus.procesos.modelado.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** PR 55: lo que se cambia de un artefacto. El tipo no: cambiaria el elemento BPMN que es. */
public record EditarArtefactoRequest(
        @Schema(description = "Text of an annotation, or the name of a group, data object or data store",
                example = "Stock is checked by hand on Mondays")
        @Size(max = 1000, message = "El texto no puede superar 1000 caracteres.") String texto,
        @Schema(description = "Pool it belongs to; required for a data object or data store unless a lane is given",
                example = "3") Long poolId,
        @Schema(description = "Lane it sits in; its position is then relative to the lane, like a node",
                example = "4") Long laneId,
        @Schema(description = "Horizontal position", example = "300") int posicionX,
        @Schema(description = "Vertical position", example = "120") int posicionY,
        @Schema(description = "Width; empty takes the default of its type", example = "100")
        @Positive(message = "El ancho tiene que ser positivo.") Integer ancho,
        @Schema(description = "Height; empty takes the default of its type", example = "30")
        @Positive(message = "El alto tiene que ser positivo.") Integer alto,
        @Schema(description = "Version read with the last GET; if someone saved a change since, the edit answers 409",
                example = "0")
        @NotNull(message = "La versión es obligatoria.") Long version) {
}
