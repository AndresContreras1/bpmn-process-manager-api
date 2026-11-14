package com.facimus.procesos.ejecucion.dto.request;

import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/** PR 68: una prueba del borrador. Como abrir un caso, sin modo: una prueba siempre corre en PRUEBA. */
@Schema(description = "A test run of the draft of a process")
public record PruebaRequest(
        @Schema(description = "What the messages of the test case will be matched by", example = "TEST-1")
        @Size(max = 120, message = "La referencia no puede superar 120 caracteres.") String referencia,
        @Schema(description = "Case variables the conditions of the gateways will read",
                example = "{\"order\": {\"total\": 150}}") Map<String, Object> variables) {
}
