package com.facimus.procesos.ejecucion.dto.response;

import com.facimus.procesos.modelado.dto.response.FormularioResponse;

import io.swagger.v3.oas.annotations.media.Schema;

/** PR 69: el formulario con que se abre a mano un caso de la version vigente del proceso. */
@Schema(description = "The form a case of the current version opens with")
public record FormularioDeInicioResponse(
        @Schema(example = "7") Long procesoId,
        @Schema(description = "Number of the current version", example = "3") Integer version,
        @Schema(description = "The start event", example = "Refund requested") String evento,
        @Schema(description = "The form; empty when the start has none, and then a case opens without variables "
                + "outside PRUEBA mode") FormularioResponse formulario) {
}
