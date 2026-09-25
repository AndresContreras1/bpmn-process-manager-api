package com.facimus.procesos.ejecucion.dto.response;

import com.facimus.procesos.ejecucion.model.EstadoCaso;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "How many cases are in one state")
public record CasosPorEstadoResponse(
        @Schema(example = "ABIERTO") EstadoCaso estado,
        @Schema(example = "12") long cantidad) {
}
