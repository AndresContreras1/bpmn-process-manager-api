package com.facimus.procesos.modelado.dto.response;

import com.facimus.procesos.modelado.model.Severidad;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "One finding of the review: what is wrong and what to do about it")
public record HallazgoResponse(
        @Schema(example = "ALTA") Severidad severidad,
        @Schema(description = "The element it is about, named as the diagram names it",
                example = "Gateway: Stock available?") String elemento,
        @Schema(example = "Two flows leave the gateway and neither says when to take it") String problema,
        @Schema(example = "Give each flow a condition, or make the gateway parallel") String sugerencia) {
}
