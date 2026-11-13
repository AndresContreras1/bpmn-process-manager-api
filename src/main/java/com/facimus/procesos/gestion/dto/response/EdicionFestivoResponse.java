package com.facimus.procesos.gestion.dto.response;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A change the organization made to its country's public holidays")
public record EdicionFestivoResponse(
        @Schema(example = "2026-06-08") LocalDate fecha,
        @Schema(description = "Name the day has as a holiday; empty when the change removes the holiday",
                example = "King's Birthday") String nombre,
        @Schema(description = "True when the day is a holiday after the change, false when the change removes it",
                example = "true") boolean festivo) {
}
