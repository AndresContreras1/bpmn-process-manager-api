package com.facimus.procesos.common.condiciones;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Los seis comparadores del lenguaje. Comparar es lo unico que una condicion sabe hacer, y solo entre valores del
 * mismo tipo: numero con numero, texto con texto y booleano con booleano.
 *
 * <p>Cuando los tipos no se pueden comparar la respuesta es que no son iguales, no un fallo: el caso ya esta
 * corriendo y una variable con el tipo equivocado no puede tumbarlo. Mezclar tipos a mano se atrapa al publicar,
 * que es cuando todavia hay alguien mirando (E-08).
 */
public enum Operador {

    IGUAL("=="),
    DISTINTO("!="),
    MAYOR(">"),
    MAYOR_O_IGUAL(">="),
    MENOR("<"),
    MENOR_O_IGUAL("<=");

    private final String simbolo;

    Operador(String simbolo) {
        this.simbolo = simbolo;
    }

    public String simbolo() {
        return simbolo;
    }

    /** El operador que se escribe asi, o vacio si ese simbolo no es ninguno. */
    static Optional<Operador> delSimbolo(String simbolo) {
        for (Operador operador : values()) {
            if (operador.simbolo.equals(simbolo)) {
                return Optional.of(operador);
            }
        }
        return Optional.empty();
    }

    /** Los que ponen dos valores en una recta; los otros dos solo miran si son el mismo. */
    public boolean ordena() {
        return this != IGUAL && this != DISTINTO;
    }

    /** Si el valor del caso cumple lo que esta comparacion pide del literal escrito en la condicion. */
    boolean compara(Object valor, Object literal) {
        if (!ordena()) {
            boolean iguales = sonIguales(valor, literal);
            return this == IGUAL ? iguales : !iguales;
        }
        OptionalInt orden = comparar(valor, literal);
        if (orden.isEmpty()) {
            return false;
        }
        return switch (this) {
            case MAYOR -> orden.getAsInt() > 0;
            case MAYOR_O_IGUAL -> orden.getAsInt() >= 0;
            case MENOR -> orden.getAsInt() < 0;
            default -> orden.getAsInt() <= 0;
        };
    }

    private static boolean sonIguales(Object valor, Object literal) {
        if (literal instanceof Boolean) {
            return literal.equals(valor);
        }
        if (literal instanceof BigDecimal numero) {
            return comoNumero(valor).map(otro -> otro.compareTo(numero) == 0).orElse(false);
        }
        return valor instanceof String texto && texto.equals(literal);
    }

    private static OptionalInt comparar(Object valor, Object literal) {
        if (literal instanceof BigDecimal numero) {
            return comoNumero(valor).map(otro -> OptionalInt.of(otro.compareTo(numero))).orElseGet(OptionalInt::empty);
        }
        if (literal instanceof String texto && valor instanceof String otro) {
            return compararFechas(otro, texto);
        }
        return OptionalInt.empty();
    }

    /**
     * Dos textos solo se ponen en una recta si los dos son fechas ISO. Comparar textos por orden alfabetico seria
     * responder cualquier cosa a {@code estado > "APPROVED"}, que no quiere decir nada.
     */
    private static OptionalInt compararFechas(String valor, String literal) {
        Optional<LocalDateTime> izquierda = comoFecha(valor);
        Optional<LocalDateTime> derecha = comoFecha(literal);
        if (izquierda.isEmpty() || derecha.isEmpty()) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(izquierda.orElseThrow().compareTo(derecha.orElseThrow()));
    }

    /** El valor de una variable llega del JSON como numero; un texto que parece un numero no lo es. */
    private static Optional<BigDecimal> comoNumero(Object valor) {
        return valor instanceof Number numero ? Optional.of(new BigDecimal(numero.toString())) : Optional.empty();
    }

    /** Una fecha sin hora se compara desde su primer instante, para que el dia entero quede a un solo lado. */
    static Optional<LocalDateTime> comoFecha(String texto) {
        try {
            return Optional.of(LocalDate.parse(texto).atStartOfDay());
        } catch (DateTimeParseException noEsUnDia) {
            return conHora(texto);
        }
    }

    private static Optional<LocalDateTime> conHora(String texto) {
        try {
            return Optional.of(LocalDateTime.parse(texto));
        } catch (DateTimeParseException noEsUnaFecha) {
            return Optional.empty();
        }
    }
}
