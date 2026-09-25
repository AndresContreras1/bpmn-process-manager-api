package com.facimus.procesos.ejecucion.dto.request;

import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Lo que deja quien completa una tarea. Los datos entran a las variables del caso bajo
 * {@code tarea.<nombre del nodo en camello>}, asi que un gateway posterior puede preguntar por ellos.
 */
@Schema(description = "What the person who completes the task hands over")
public record CompletarTareaRequest(
        @Schema(description = "Free key-value data; it lands in the case variables under tarea.<taskNameInCamel>",
                example = "{\"packedItems\": 3}") Map<String, Object> datos) {
}
