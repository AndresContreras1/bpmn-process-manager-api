package com.facimus.procesos.modelado.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/** PR 62: la ficha de caracterizacion de un proceso. Cada campo es texto libre, y vacio vale. */
@Schema(description = "The characterization sheet of a process; every field is free text and may be empty")
public record CaracterizacionRequest(
        @Schema(example = "Deliver every order complete and on time") @Size(max = 4000, message = LARGO) String objetivo,
        @Schema(example = "From the paid order to its delivery") @Size(max = 4000, message = LARGO) String alcance,
        @Schema(example = "Paid orders") @Size(max = 4000, message = LARGO) String entradas,
        @Schema(example = "Delivered orders, tracking numbers") @Size(max = 4000, message = LARGO) String salidas,
        @Schema(example = "Payment gateway, carrier") @Size(max = 4000, message = LARGO) String proveedores,
        @Schema(example = "Online customers") @Size(max = 4000, message = LARGO) String clientes,
        @Schema(example = "Orders shipped within 24 h") @Size(max = 4000, message = LARGO) String indicadores,
        @Schema(example = "Stock out, carrier delay") @Size(max = 4000, message = LARGO) String riesgos,
        @Schema(example = "Consumer protection statute") @Size(max = 4000, message = LARGO) String normativa,
        @Schema(description = "Version read with the last GET; empty the first time", example = "0") Long version) {

    static final String LARGO = "Un campo de la ficha no puede superar 4000 caracteres.";
}
