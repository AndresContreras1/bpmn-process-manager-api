package com.facimus.procesos.modelado.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "New label and condition of a sequence flow; its nodes cannot change")
public record EditarArcoRequest(
        @Schema(example = "Declined") String etiqueta,
        @Schema(example = "payment.status == DECLINED") String condicion) {
}
