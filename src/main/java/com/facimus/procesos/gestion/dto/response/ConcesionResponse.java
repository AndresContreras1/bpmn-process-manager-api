package com.facimus.procesos.gestion.dto.response;

import com.facimus.procesos.common.model.Accion;
import com.facimus.procesos.gestion.model.SujetoConcesion;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "An action granted on a folder or a process")
public record ConcesionResponse(
        @Schema(example = "1") Long id,
        @Schema(description = "The folder; empty if it is on a process", example = "2") Long carpetaId,
        @Schema(description = "The process; empty if it is on a folder", example = "3") Long procesoId,
        @Schema(example = "PERSONA") SujetoConcesion sujeto,
        @Schema(example = "4") Long usuarioId,
        @Schema(example = "5") Long rolProcesoId,
        @Schema(example = "MODELAR") Accion accion) {
}
