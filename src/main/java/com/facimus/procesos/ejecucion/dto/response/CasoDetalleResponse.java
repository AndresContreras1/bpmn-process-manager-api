package com.facimus.procesos.ejecucion.dto.response;

import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;

/** El caso con todo lo suyo: por donde ha pasado y con que variables esta decidiendo. */
@Schema(description = "A case with the steps it went through and the variables it is deciding with")
public record CasoDetalleResponse(
        CasoResponse caso,
        @Schema(description = "In the order they happened") List<PasoDelCasoResponse> pasos,
        @Schema(description = "What the gateway conditions read", example = "{\"payment\": {\"status\": \"APPROVED\"}}")
        Map<String, Object> variables) {
}
