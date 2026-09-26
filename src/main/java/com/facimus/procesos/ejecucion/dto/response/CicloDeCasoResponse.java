package com.facimus.procesos.ejecucion.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Cuanto tarda un pedido de punta a punta, contado en ticks del reloj de la tienda y no en horas: el tiempo de la
 * simulacion es el que se puede repetir.
 */
@Schema(description = "How long an order takes end to end, in ticks of the store's clock")
public record CicloDeCasoResponse(
        @Schema(description = "Cases the number is made of; only the finished ones count", example = "20")
        long terminados,
        @Schema(description = "Ticks from opening to finishing, on average", example = "6.4") double medio,
        @Schema(description = "Ticks that nineteen out of twenty stay under", example = "9") int p95) {

    /** Sin pedidos terminados no hay tiempo de ciclo: cero no seria rapido, seria mentira. */
    public static CicloDeCasoResponse sinDatos() {
        return new CicloDeCasoResponse(0, 0, 0);
    }
}
