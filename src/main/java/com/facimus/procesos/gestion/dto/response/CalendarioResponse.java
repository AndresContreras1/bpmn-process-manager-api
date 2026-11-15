package com.facimus.procesos.gestion.dto.response;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

import com.facimus.procesos.gestion.model.Pais;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "The business calendar of the organization: what business time is counted with")
public record CalendarioResponse(
        @Schema(description = "Country whose public holidays are computed", example = "CO") Pais pais,
        @Schema(description = "IANA time zone the working hours are read in", example = "America/Bogota")
        String zonaHoraria,
        @Schema(example = "08:00") LocalTime horaInicio,
        @Schema(example = "17:00") LocalTime horaFin,
        @Schema(description = "Working days of the week, Monday first") List<DayOfWeek> diasHabiles,
        @Schema(description = "Send it back when editing; empty while the calendar was never saved, which means it "
                + "is the default of the organization's country", example = "0") Long version) {
}
