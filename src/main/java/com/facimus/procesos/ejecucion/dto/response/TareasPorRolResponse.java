package com.facimus.procesos.ejecucion.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "How much work one process role has waiting")
public record TareasPorRolResponse(
        @Schema(example = "2") Long rolProcesoId,
        @Schema(example = "Warehouse") String rolNombre,
        @Schema(example = "7") long tareas) {
}
