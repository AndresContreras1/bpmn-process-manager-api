package com.facimus.procesos.modelado.dto.response;

import java.time.LocalDateTime;

import com.facimus.procesos.modelado.model.TipoArtefacto;

import io.swagger.v3.oas.annotations.media.Schema;

/** PR 55: una anotacion, un grupo, un objeto de datos o un almacen del diagrama. */
@Schema(description = "A text annotation, group, data object or data store")
public record ArtefactoResponse(
        @Schema(example = "40") Long id,
        @Schema(description = "BPMN id, stable for its whole life", example = "TextAnnotation_3c1a0f9e2b7d")
        String bpmnId,
        @Schema(example = "ANOTACION") TipoArtefacto tipo,
        @Schema(description = "The annotation's text, or the name of the rest", example = "Paid by card or transfer")
        String texto,
        @Schema(description = "Pool it belongs to; empty for one drawn outside every pool", example = "1") Long poolId,
        @Schema(description = "Lane it is drawn in, relative to it; empty when drawn outside every lane",
                example = "3") Long laneId,
        @Schema(example = "220") int posicionX,
        @Schema(example = "40") int posicionY,
        @Schema(example = "100") int ancho,
        @Schema(example = "30") int alto,
        @Schema(description = "Send it back when editing; it goes up with every saved change", example = "0")
        Long version,
        @Schema(description = "Id of the user who created it", example = "2") Long creadoPor,
        @Schema(example = "2026-11-10T15:00:00") LocalDateTime fechaCreacion,
        @Schema(description = "Id of the user who saved the last change", example = "5") Long modificadoPor,
        @Schema(example = "2026-11-10T15:30:00") LocalDateTime fechaModificacion) {
}
