package com.facimus.procesos.modelado.dto.response;

import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;

/** PR 54: que paso con cada cosa del archivo. */
@Schema(description = "What happened to each part of an imported BPMN file")
public record InformeImportacionResponse(
        @Schema(description = "How many of each kind were imported as they were",
                example = "{\"pools\": 2, \"lanes\": 2, \"tareas\": 5, \"flujos\": 7}") Map<String, Integer> soportados,
        @Schema(description = "Elements turned into the closest thing the model has, and how")
        List<HallazgoImportacionResponse> convertidos,
        @Schema(description = "Elements the model does not have, left out with whatever depended on them")
        List<HallazgoImportacionResponse> noSoportados,
        @Schema(description = "Keys of the elements whose content from another tool was kept, to export it again",
                example = "[\"Activity_1\"]") List<String> extensionesConservadas,
        @Schema(description = "What the file breaks of the OMG schema; it was read anyway",
                example = "[\"Linea 12: cvc-complex-type.2.4.a ...\"]") List<String> avisosDelEsquema) {
}
