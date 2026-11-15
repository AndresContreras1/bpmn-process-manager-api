package com.facimus.procesos.gestion.model;

import java.time.DayOfWeek;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** PR 68: los dias habiles de un calendario, guardados como sus nombres en ingles separados por comas y en orden. */
@Converter
public class DiasDeLaSemanaConverter implements AttributeConverter<Set<DayOfWeek>, String> {

    @Override
    public String convertToDatabaseColumn(Set<DayOfWeek> dias) {
        return dias == null ? "" : dias.stream().sorted().map(DayOfWeek::name).collect(Collectors.joining(","));
    }

    @Override
    public Set<DayOfWeek> convertToEntityAttribute(String guardado) {
        EnumSet<DayOfWeek> dias = EnumSet.noneOf(DayOfWeek.class);
        if (guardado != null && !guardado.isBlank()) {
            Arrays.stream(guardado.split(",")).map(String::strip).map(DayOfWeek::valueOf).forEach(dias::add);
        }
        return dias;
    }
}
