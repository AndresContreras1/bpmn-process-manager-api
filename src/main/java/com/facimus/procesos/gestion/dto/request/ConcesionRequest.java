package com.facimus.procesos.gestion.dto.request;

import com.facimus.procesos.common.model.Accion;
import com.facimus.procesos.gestion.model.SujetoConcesion;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "An action granted on a folder or a process")
public record ConcesionRequest(
        @Schema(example = "MODELAR") @NotNull(message = "La acción es obligatoria.") Accion accion,
        @Schema(example = "PERSONA") @NotNull(message = "El sujeto es obligatorio.") SujetoConcesion sujeto,
        @Schema(description = "The user, for PERSONA", example = "2") Long usuarioId,
        @Schema(description = "The process role, for ROL", example = "3") Long rolProcesoId) {
}
