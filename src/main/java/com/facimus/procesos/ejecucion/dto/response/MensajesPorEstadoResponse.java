package com.facimus.procesos.ejecucion.dto.response;

import com.facimus.procesos.ejecucion.model.EstadoMensajeSaliente;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "How many sent messages are in one state")
public record MensajesPorEstadoResponse(
        @Schema(example = "PENDIENTE") EstadoMensajeSaliente estado,
        @Schema(example = "3") long cantidad) {
}
