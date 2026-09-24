package com.facimus.procesos.modelado.dto.request;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;

/** El orden nuevo de los hijos de un elemento: la lista completa, del primero al ultimo. */
@Schema(description = "The new order of the children of an element: the complete list, first to last")
public record OrdenRequest(
        @Schema(description = "Every child of the element, exactly once, in the order they should be drawn",
                example = "[4, 7, 5]")
        @NotEmpty(message = "La lista de orden es obligatoria.") List<Long> ids) {
}
