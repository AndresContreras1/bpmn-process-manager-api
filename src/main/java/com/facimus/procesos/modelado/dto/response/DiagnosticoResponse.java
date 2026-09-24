package com.facimus.procesos.modelado.dto.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/** Lo que el diagrama tiene mal, en una lista determinista: el mismo diagrama da siempre los mismos hallazgos. */
@Schema(description = "What a diagram gets wrong, checked against the modeling rules; the same diagram always "
        + "gives the same findings, in the same order. With sinElemento it is the diagram that would be left after "
        + "deleting that element")
public record DiagnosticoResponse(
        @Schema(example = "1") Long procesoId,
        @Schema(description = "The element whose deletion was simulated, when one was asked for",
                example = "GATEWAY:12") String sinElemento,
        @Schema(description = "How many findings block publishing", example = "2") int errores,
        @Schema(description = "How many findings are only warnings", example = "3") int advertencias,
        @Schema(description = "Errors first, then warnings; inside each severity, by code and by element")
        List<HallazgoDiagnosticoResponse> hallazgos) {
}
