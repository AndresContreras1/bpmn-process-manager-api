package com.facimus.procesos.gestion.controller.dto;

import java.util.List;

public record ProcesoDetalleResponse(ProcesoResponse proceso, List<HistorialCambioResponse> historial) {
}
