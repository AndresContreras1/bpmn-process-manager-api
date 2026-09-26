package com.facimus.procesos.ejecucion.dto.response;

import com.facimus.procesos.ejecucion.model.ResultadoCorrelacion;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "How many received messages ended one way")
public record MensajesPorResultadoResponse(
        @Schema(example = "ENTREGADO_A_CASO") ResultadoCorrelacion resultado,
        @Schema(example = "18") long cantidad) {
}
