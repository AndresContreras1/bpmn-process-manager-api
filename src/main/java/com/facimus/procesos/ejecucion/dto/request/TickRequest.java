package com.facimus.procesos.ejecucion.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Schema(description = "How far to move the store's simulation clock")
public record TickRequest(
        @Schema(description = "Ticks to advance, from 1 to 100", example = "1")
        @NotNull(message = "El número de ticks es obligatorio.")
        @Min(value = 1, message = "El reloj se mueve entre 1 y 100 ticks por vez.")
        @Max(value = 100, message = "El reloj se mueve entre 1 y 100 ticks por vez.") Integer ticks) {
}
