package com.facimus.procesos.modelado.dto.response;

import com.facimus.procesos.modelado.model.Severidad;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "One finding of the diagnosis: what is wrong, where, and what to do about it")
public record HallazgoDiagnosticoResponse(
        @Schema(description = "Code of the catalogue; E codes are errors and block publishing, A codes are warnings",
                example = "E-05") String codigo,
        @Schema(example = "ALTA") Severidad severidad,
        @Schema(description = "The element it is about, named as the diagram names it; empty when it is about the "
                + "whole process", example = "Actividad \"Pick and pack items\"") String elemento,
        @Schema(description = "Id of that element, to select it in the editor", example = "10") Long elementoId,
        @Schema(example = "No sale ningun flujo de este nodo y no es un evento de fin.") String problema,
        @Schema(example = "Conectalo con el siguiente paso, o termina el camino con un evento de fin.")
        String sugerencia) {
}
