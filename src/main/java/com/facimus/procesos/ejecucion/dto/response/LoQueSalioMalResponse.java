package com.facimus.procesos.ejecucion.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Lo que no salio como se esperaba, contado de las lineas de tiempo de los casos. Son las tres cosas que dejan un
 * pedido parado o torcido, y cada una se arregla de una forma distinta.
 */
@Schema(description = "What did not go as expected, counted from the case timelines")
public record LoQueSalioMalResponse(
        @Schema(description = "Messages that did not reach the partner", example = "2") long enviosFallidos,
        @Schema(description = "Gateways that found no path, which leave the case in ERROR", example = "1")
        long sinCamino,
        @Schema(description = "Conditions that asked for a variable the case did not have", example = "3")
        long variablesAusentes) {
}
