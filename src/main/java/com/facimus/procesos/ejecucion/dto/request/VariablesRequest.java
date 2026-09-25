package com.facimus.procesos.ejecucion.dto.request;

import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** Las variables que el administrador corrige en un caso que se quedo sin camino, antes de reintentarlo. */
@Schema(description = "The case variables, replaced as a whole")
public record VariablesRequest(
        @Schema(description = "Replaces the variables of the case", example = "{\"payment\": {\"status\": \"APPROVED\"}}")
        @NotNull(message = "Las variables son obligatorias.") Map<String, Object> variables,
        @Schema(description = "The version that was read; it comes in every GET of the case", example = "0")
        @NotNull(message = "La version es obligatoria.") Long version) {
}
