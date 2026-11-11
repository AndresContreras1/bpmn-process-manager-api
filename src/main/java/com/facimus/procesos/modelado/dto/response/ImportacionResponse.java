package com.facimus.procesos.modelado.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** PR 54: lo que paso al importar un archivo BPMN: el proceso, cuanto cambio y el informe elemento por elemento. */
@Schema(description = "The result of importing a BPMN file")
public record ImportacionResponse(
        @Schema(description = "The process the file went into", example = "7") Long procesoId,
        @Schema(example = "Order fulfillment") String nombre,
        @Schema(description = "Elements created in the draft", example = "14") int creados,
        @Schema(description = "Elements of the draft that changed", example = "2") int editados,
        @Schema(description = "Elements of the draft the file no longer has, now deleted", example = "1")
        int dadosDeBaja,
        InformeImportacionResponse informe) {
}
