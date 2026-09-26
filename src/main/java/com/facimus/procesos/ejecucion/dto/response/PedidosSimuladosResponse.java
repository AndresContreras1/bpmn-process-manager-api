package com.facimus.procesos.ejecucion.dto.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A batch of orders from the simulated customer, and what the process did with them")
public record PedidosSimuladosResponse(
        @Schema(description = "Message they came in as, the one that opens a case of this process",
                example = "Order placed") String mensaje,
        @Schema(description = "How many were sent", example = "20") int pedidos,
        @Schema(description = "How many opened a case; the rest say why in the inbox", example = "20")
        int casosNuevos,
        @Schema(description = "The reference of each case that was opened") List<String> referencias) {
}
