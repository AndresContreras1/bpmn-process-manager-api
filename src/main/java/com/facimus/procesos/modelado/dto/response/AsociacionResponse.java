package com.facimus.procesos.modelado.dto.response;

import java.time.LocalDateTime;

import com.facimus.procesos.modelado.model.DireccionAsociacion;
import com.facimus.procesos.modelado.model.TipoAsociacion;

import io.swagger.v3.oas.annotations.media.Schema;

/** PR 55: lo que une un artefacto con otro elemento, por sus bpmnId. */
@Schema(description = "An association, or data going into or out of a task")
public record AsociacionResponse(
        @Schema(example = "41") Long id,
        @Schema(description = "BPMN id, stable for its whole life", example = "Association_0d9c1b2e3f4a")
        String bpmnId,
        @Schema(example = "ASOCIACION") TipoAsociacion tipo,
        @Schema(description = "bpmnId of where it starts", example = "TextAnnotation_3c1a0f9e2b7d") String origen,
        @Schema(description = "bpmnId of where it ends", example = "Activity_1f3a9c0b7e42") String destino,
        @Schema(example = "NINGUNA") DireccionAsociacion direccion,
        @Schema(description = "Send it back when editing; it goes up with every saved change", example = "0")
        Long version,
        @Schema(description = "Id of the user who created it", example = "2") Long creadoPor,
        @Schema(example = "2026-11-10T15:00:00") LocalDateTime fechaCreacion,
        @Schema(description = "Id of the user who saved the last change", example = "5") Long modificadoPor,
        @Schema(example = "2026-11-10T15:30:00") LocalDateTime fechaModificacion) {
}
