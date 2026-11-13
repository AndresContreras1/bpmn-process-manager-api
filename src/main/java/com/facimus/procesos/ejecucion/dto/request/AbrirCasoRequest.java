package com.facimus.procesos.ejecucion.dto.request;

import java.util.Map;

import com.facimus.procesos.ejecucion.model.ModoCaso;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * Lo que hace falta para abrir un caso a mano. Las dos cosas son opcionales: un proceso que no espera mensajes no
 * necesita referencia, y uno que pregunta por sus variables en un gateway las recibira mas adelante.
 */
@Schema(description = "A new case (an order) of a published process")
public record AbrirCasoRequest(
        @Schema(description = "What the messages of this case will be matched by, usually the order number",
                example = "ORD-1001")
        @Size(max = 120, message = "La referencia no puede superar 120 caracteres.") String referencia,
        @Schema(description = "Case variables the conditions of the gateways will read: the answers to the start "
                + "form when it has one; free variables only in PRUEBA mode",
                example = "{\"order\": {\"total\": 150}}") Map<String, Object> variables,
        @Schema(description = "PRODUCCION runs on the wall clock and the business calendar; PRUEBA, the default, on "
                + "the store clock ticks and the simulated partners", example = "PRODUCCION") ModoCaso modo) {

    /** PR 68: el modo con que se abre: el que diga, o PRUEBA, como todos los casos de antes. */
    public ModoCaso modoDelCaso() {
        return modo == null ? ModoCaso.PRUEBA : modo;
    }
}
