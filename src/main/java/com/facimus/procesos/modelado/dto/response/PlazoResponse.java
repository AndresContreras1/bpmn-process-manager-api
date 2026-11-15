package com.facimus.procesos.modelado.dto.response;

import com.facimus.procesos.modelado.model.UnidadDePlazo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * PR 71: el plazo de una tarea de usuario o de un caso entero, en un evento de inicio. Un nodo sin plazo no trae
 * ninguno: asi una version publicada antes de este PR sigue teniendo la misma forma.
 */
@Schema(description = "The deadline of a user task, or of the whole case on a start event; absent when it has none")
public record PlazoResponse(
        @Schema(description = "How many units: 1 to 8784 hours or 1 to 366 business days", example = "2")
        int cantidad,
        @Schema(description = "HORAS: wall-clock hours; DIAS_HABILES: business days of the organization's calendar",
                example = "DIAS_HABILES") UnidadDePlazo unidad) {
}
