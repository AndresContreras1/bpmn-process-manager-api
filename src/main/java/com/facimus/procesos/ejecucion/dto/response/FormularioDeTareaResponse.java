package com.facimus.procesos.ejecucion.dto.response;

import java.util.Map;

import com.facimus.procesos.modelado.dto.response.FormularioResponse;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * PR 69: lo que necesita quien va a completar una tarea: el formulario congelado en la version del caso, lo que se le
 * propone en cada campo y las variables del caso, que las condiciones del formulario tambien leen.
 */
@Schema(description = "The form of a task, as frozen in the version of its case")
public record FormularioDeTareaResponse(
        @Schema(example = "31") Long tareaId,
        @Schema(example = "12") Long casoId,
        @Schema(description = "The task, as named in the diagram", example = "Approve the refund") String nodo,
        @Schema(description = "The form; empty when the task has none, and then it takes no data outside PRUEBA mode")
        FormularioResponse formulario,
        @Schema(description = "The value offered in each field, from the case variable its porDefecto names",
                example = "{\"monto\": 150}") Map<String, Object> valores,
        @Schema(description = "The case variables, which visibleSi and requeridoSi may read",
                example = "{\"order\": {\"total\": 150}, \"caso\": {\"referencia\": \"ORD-1001\"}}")
        Map<String, Object> variables) {
}
