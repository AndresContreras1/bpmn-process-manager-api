package com.facimus.procesos.ejecucion.dto.request;

import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Schema(description = "A batch of orders from the simulated customer")
public record PedidosSimuladosRequest(
        @Schema(description = "Process they go to; it has to be one that starts with a message", example = "1")
        @NotNull(message = "El proceso es obligatorio.") Long procesoId,
        @Schema(description = "How many orders, from 1 to 200", example = "20")
        @NotNull(message = "La cantidad es obligatoria.")
        @Min(value = 1, message = "Se piden entre 1 y 200 pedidos por vez.")
        @Max(value = 200, message = "Se piden entre 1 y 200 pedidos por vez.") Integer cantidad,
        @Schema(description = "What every order carries the same; what it brings wins over what the simulated "
                + "customer makes up", example = "{\"channel\": \"web\"}") Map<String, Object> plantilla) {
}
