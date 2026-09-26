package com.facimus.procesos.ejecucion.dto.response;

import com.facimus.procesos.modelado.model.Integracion;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "How many messages are waiting for one kind of partner")
public record PendientesPorSocioResponse(
        @Schema(example = "PAGOS") Integracion socio,
        @Schema(example = "3") long cantidad) {
}
