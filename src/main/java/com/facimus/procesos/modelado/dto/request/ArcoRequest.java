package com.facimus.procesos.modelado.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

@Schema(description = "A sequence flow between two flow nodes of the same pool")
public record ArcoRequest(
        @Schema(description = "Source node: an activity or a gateway", example = "12")
        @NotNull(message = "El nodo de origen es obligatorio.") Long origenId,
        @Schema(description = "Target node, in the same pool as the source", example = "10")
        @NotNull(message = "El nodo de destino es obligatorio.") Long destinoId,
        @Schema(example = "Approved")
        @Size(max = 120, message = "La etiqueta no puede superar 120 caracteres.") String etiqueta,
        @Schema(description = "Required when the source is an exclusive or inclusive gateway, unless the flow "
                + "is its default one", example = "payment.status == APPROVED")
        @Size(max = 500, message = "La condicion no puede superar 500 caracteres.") String condicion,
        @Schema(description = "The flow a deciding gateway takes when no condition holds; it carries no "
                + "condition, and a gateway has at most one", example = "false") Boolean porDefecto,
        @Schema(description = "Order in which the gateway evaluates its outgoing flows; ties are broken by id",
                example = "0")
        @PositiveOrZero(message = "El orden no puede ser negativo.") Integer orden) {
}
