package com.facimus.procesos.modelado.service.impl;

import java.time.Duration;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;

import com.facimus.procesos.modelado.model.TipoTemporizador;

/**
 * PR 55: si un temporizador dice cuando salta, en ISO 8601 como lo pide BPMN: una duracion (P3D, PT2H), una fecha y
 * hora (2026-12-24T09:00:00Z) o un ciclo de repeticiones (R3/PT1H, R/2026-01-01T08:00:00Z/P1D).
 */
final class ExpresionDeTemporizador {

    private ExpresionDeTemporizador() {
    }

    /** Lo que esta mal, o vacio si se entiende. */
    static Optional<String> problema(TipoTemporizador tipo, String expresion) {
        if (expresion == null || expresion.isBlank()) {
            return Optional.of("Este temporizador no dice cuando salta.");
        }
        if (tipo == null) {
            return Optional.of("Este temporizador no dice si espera una duracion, una fecha o un ciclo.");
        }
        String texto = expresion.strip();
        boolean valida = switch (tipo) {
            case DURACION -> esDuracion(texto);
            case FECHA -> esFecha(texto);
            case CICLO -> esCiclo(texto);
        };
        return valida ? Optional.empty() : Optional.of("\"" + texto + "\" no es " + switch (tipo) {
            case DURACION -> "una duracion";
            case FECHA -> "una fecha y hora";
            case CICLO -> "un ciclo";
        } + " en ISO 8601.");
    }

    static boolean esDuracion(String texto) {
        try {
            Duration.parse(texto);
            return true;
        } catch (DateTimeParseException noEsDeTiempo) {
            try {
                Period.parse(texto);
                return true;
            } catch (DateTimeParseException tampocoDeCalendario) {
                return false;
            }
        }
    }

    static boolean esFecha(String texto) {
        try {
            DateTimeFormatter.ISO_DATE_TIME.parse(texto);
            return true;
        } catch (DateTimeParseException noEsFecha) {
            return false;
        }
    }

    /** R, con o sin cuantas veces, y uno o dos tramos: una duracion, o un inicio y una duracion, o al reves. */
    static boolean esCiclo(String texto) {
        String[] partes = texto.split("/", -1);
        if (partes.length < 2 || partes.length > 3 || !repeticiones(partes[0])) {
            return false;
        }
        boolean conDuracion = false;
        for (int i = 1; i < partes.length; i++) {
            if (esDuracion(partes[i])) {
                conDuracion = true;
            } else if (!esFecha(partes[i])) {
                return false;
            }
        }
        return conDuracion;
    }

    private static boolean repeticiones(String parte) {
        if (parte.isEmpty() || parte.charAt(0) != 'R') {
            return false;
        }
        for (int i = 1; i < parte.length(); i++) {
            if (!Character.isDigit(parte.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
