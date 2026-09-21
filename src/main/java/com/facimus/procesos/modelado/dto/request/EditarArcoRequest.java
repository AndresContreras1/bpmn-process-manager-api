package com.facimus.procesos.modelado.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "New label and condition of a sequence flow; its nodes cannot change")
public record EditarArcoRequest(
        @Schema(example = "Declined")
        @Size(max = 120, message = "La etiqueta no puede superar 120 caracteres.") String etiqueta,
        @Schema(example = "payment.status == DECLINED")
        @Size(max = 500, message = "La condicion no puede superar 500 caracteres.") String condicion) {
}
