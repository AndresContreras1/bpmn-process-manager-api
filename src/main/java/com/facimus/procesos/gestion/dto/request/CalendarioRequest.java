package com.facimus.procesos.gestion.dto.request;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Set;

import com.facimus.procesos.gestion.model.Pais;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "The business calendar of the organization")
public record CalendarioRequest(
        @Schema(description = "Country whose public holidays are computed", example = "CO")
        @NotNull(message = "El país es obligatorio.") Pais pais,
        @Schema(description = "IANA time zone the working hours are read in", example = "America/Bogota")
        @NotBlank(message = "La zona horaria es obligatoria.")
        @Size(max = 40, message = "La zona horaria no puede superar 40 caracteres.") String zonaHoraria,
        @Schema(description = "When the working day starts, in that zone", example = "08:00")
        @NotNull(message = "La hora de inicio es obligatoria.") LocalTime horaInicio,
        @Schema(description = "When the working day ends; after the start", example = "17:00")
        @NotNull(message = "La hora de fin es obligatoria.") LocalTime horaFin,
        @Schema(description = "Working days of the week", example = "[\"MONDAY\", \"TUESDAY\", \"WEDNESDAY\", "
                + "\"THURSDAY\", \"FRIDAY\"]")
        @NotEmpty(message = "Un calendario tiene al menos un día hábil.")
        Set<@NotNull(message = "Un día hábil es un día de la semana.") DayOfWeek> diasHabiles,
        @Schema(description = "Version read with the last GET; empty while the calendar was never saved. If someone "
                + "saved a change since, the edit answers 409", example = "0") Long version) {
}
