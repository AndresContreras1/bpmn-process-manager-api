package com.facimus.procesos.ejecucion.dto.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "What a case sent and what it received, in the order it happened")
public record MensajesDelCasoResponse(
        @Schema(description = "What the case sent") List<MensajeSalienteResponse> salientes,
        @Schema(description = "What reached the case") List<MensajeEntranteResponse> entrantes) {
}
