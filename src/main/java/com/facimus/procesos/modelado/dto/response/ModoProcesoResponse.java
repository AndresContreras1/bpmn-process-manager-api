package com.facimus.procesos.modelado.dto.response;

import com.facimus.procesos.gestion.dto.response.ProcesoResponse;

import io.swagger.v3.oas.annotations.media.Schema;

/** PR 55: el proceso en su modo nuevo y el diagnostico de ese modo, que es lo que cambia al cambiar de modo. */
public record ModoProcesoResponse(
        @Schema(description = "The process, in its new mode") ProcesoResponse proceso,
        @Schema(description = "The diagnosis of the new mode: a documental process only checks the structure")
        DiagnosticoResponse diagnostico) {
}
