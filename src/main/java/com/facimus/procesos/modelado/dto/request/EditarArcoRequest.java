package com.facimus.procesos.modelado.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

@Schema(description = "New label, condition and nodes of a sequence flow; a node that is left out stays as it was")
public record EditarArcoRequest(
        @Schema(description = "Move the flow to start at this node; without it, it keeps the one it had",
                example = "12") Long origenId,
        @Schema(description = "Move the flow to end at this node; without it, it keeps the one it had",
                example = "10") Long destinoId,
        @Schema(example = "Declined")
        @Size(max = 120, message = "La etiqueta no puede superar 120 caracteres.") String etiqueta,
        @Schema(description = "Required when the source is an exclusive or inclusive gateway, unless the flow "
                + "is its default one", example = "payment.status == DECLINED")
        @Size(max = 500, message = "La condicion no puede superar 500 caracteres.") String condicion,
        @Schema(description = "The flow a deciding gateway takes when no condition holds; it carries no "
                + "condition, and a gateway has at most one", example = "false") Boolean porDefecto,
        @Schema(description = "Order in which the gateway evaluates its outgoing flows; ties are broken by id",
                example = "0")
        @PositiveOrZero(message = "El orden no puede ser negativo.") Integer orden,
        @Schema(description = "Version read with the last GET; if someone saved a change since, the edit answers 409",
                example = "0")
        @NotNull(message = "La versión es obligatoria.") Long version) {
}
