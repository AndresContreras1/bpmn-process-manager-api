package com.facimus.procesos.gestion.dto.response;

import java.time.LocalDate;

import com.facimus.procesos.gestion.model.OrigenFestivo;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A public holiday of the organization's calendar")
public record FestivoResponse(
        @Schema(example = "2026-06-29") LocalDate fecha,
        @Schema(example = "San Pedro y San Pablo") String nombre,
        @Schema(description = "CALCULADO when the country's law gives it, EDITADO when the organization added or "
                + "renamed it", example = "CALCULADO") OrigenFestivo origen) {
}
