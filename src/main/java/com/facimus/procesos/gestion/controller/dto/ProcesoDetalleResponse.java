package com.facimus.procesos.gestion.controller.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A process together with its change history")
public record ProcesoDetalleResponse(
        ProcesoResponse proceso,
        @Schema(description = "Changes, newest first") List<HistorialCambioResponse> historial) {
}
