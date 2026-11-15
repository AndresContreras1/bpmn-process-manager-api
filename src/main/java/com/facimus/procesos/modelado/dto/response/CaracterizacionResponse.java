package com.facimus.procesos.modelado.dto.response;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

/** PR 62: la ficha de caracterizacion de un proceso; con version vacia mientras nadie la escribio. */
@Schema(description = "The characterization sheet of a process; version is empty while nobody wrote it")
public record CaracterizacionResponse(
        @Schema(example = "Deliver every order complete and on time") String objetivo,
        @Schema(example = "From the paid order to its delivery") String alcance,
        @Schema(example = "Paid orders") String entradas,
        @Schema(example = "Delivered orders, tracking numbers") String salidas,
        @Schema(example = "Payment gateway, carrier") String proveedores,
        @Schema(example = "Online customers") String clientes,
        @Schema(example = "Orders shipped within 24 h") String indicadores,
        @Schema(example = "Stock out, carrier delay") String riesgos,
        @Schema(example = "Consumer protection statute") String normativa,
        @Schema(description = "Send it back when editing", example = "0") Long version,
        @Schema(example = "2026-11-12T10:00:00") LocalDateTime fechaModificacion) {
}
