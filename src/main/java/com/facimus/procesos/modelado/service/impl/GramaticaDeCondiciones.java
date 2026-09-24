package com.facimus.procesos.modelado.service.impl;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * La gramatica de las condiciones de un gateway. Una condicion compara el valor de una variable del caso con un
 * valor fijo, y esas comparaciones se combinan con and, or y not:
 *
 * <pre>
 * expresion   := termino ( "or" termino )*
 * termino     := factor ( "and" factor )*
 * factor      := "not" factor | "(" expresion ")" | comparacion
 * comparacion := ruta operador valor
 * ruta        := nombre ( "." nombre )*
 * operador    := "==" | "!=" | "&gt;" | "&gt;=" | "&lt;" | "&lt;="
 * valor       := numero | texto entre comillas | nombre | "true" | "false"
 * </pre>
 *
 * No hay funciones, ni asignaciones, ni nada que se ejecute: aqui la condicion solo se lee para decir si esta bien
 * escrita. Evaluarla sobre las variables de un caso es cosa del motor.
 */
final class GramaticaDeCondiciones {

    private static final Set<String> PALABRAS = Set.of("and", "or", "not", "true", "false");
    private static final Set<String> RELACIONALES = Set.of(">", ">=", "<", "<=");
    private static final String OPERADORES = "=!<>";

    private GramaticaDeCondiciones() {
    }

    /** Lo que un mensaje cita de la condicion va entre comillas, como en el resto de las reglas. */
    private static String entreComillas(String texto) {
        return "\"" + texto + "\"";
    }

    /** Por que la condicion no compila, o vacio cuando esta bien escrita. */
    static Optional<String> problema(String condicion) {
        try {
            Lector lector = new Lector(partir(condicion));
            lector.expresion();
            lector.exigirElFinal();
            return Optional.empty();
        } catch (CondicionMalEscrita mala) {
            return Optional.of(mala.getMessage());
        }
    }

    /** Un trozo con sentido propio de la condicion: un nombre, un valor, un operador o un parentesis. */
    private record Simbolo(Clase clase, String texto, int posicion) {

        boolean es(Clase otra) {
            return clase == otra;
        }

        boolean esPalabra(String palabra) {
            return clase == Clase.NOMBRE && texto.equals(palabra);
        }
    }

    private enum Clase {
        NOMBRE,
        NUMERO,
        TEXTO,
        OPERADOR,
        ABRE,
        CIERRA
    }

    /** Lo que se levanta al encontrar la falta; su mensaje es lo que el diagnostico muestra. */
    private static final class CondicionMalEscrita extends RuntimeException {

        private static final long serialVersionUID = 1L;

        CondicionMalEscrita(String mensaje) {
            super(mensaje);
        }
    }

    private static List<Simbolo> partir(String condicion) {
        List<Simbolo> simbolos = new ArrayList<>();
        int i = 0;
        while (i < condicion.length()) {
            char c = condicion.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
            } else if (c == '(' || c == ')') {
                simbolos.add(new Simbolo(c == '(' ? Clase.ABRE : Clase.CIERRA, String.valueOf(c), i));
                i++;
            } else if (c == '\'' || c == '\"') {
                i = leerTexto(condicion, i, simbolos);
            } else if (Character.isDigit(c) || (c == '-' && siguienteEsDigito(condicion, i))) {
                i = leerNumero(condicion, i, simbolos);
            } else if (Character.isLetter(c) || c == '_') {
                i = leerNombre(condicion, i, simbolos);
            } else if (OPERADORES.indexOf(c) >= 0) {
                i = leerOperador(condicion, i, simbolos);
            } else {
                throw new CondicionMalEscrita("No se entiende " + entreComillas(String.valueOf(c))
                        + " en la posicion " + (i + 1) + ".");
            }
        }
        return simbolos;
    }

    private static boolean siguienteEsDigito(String condicion, int i) {
        return i + 1 < condicion.length() && Character.isDigit(condicion.charAt(i + 1));
    }

    private static int leerTexto(String condicion, int desde, List<Simbolo> simbolos) {
        char comilla = condicion.charAt(desde);
        int cierre = condicion.indexOf(comilla, desde + 1);
        if (cierre < 0) {
            throw new CondicionMalEscrita("Falta cerrar la comilla abierta en la posicion " + (desde + 1) + ".");
        }
        simbolos.add(new Simbolo(Clase.TEXTO, condicion.substring(desde + 1, cierre), desde));
        return cierre + 1;
    }

    private static int leerNumero(String condicion, int desde, List<Simbolo> simbolos) {
        int i = desde + 1;
        boolean conPunto = false;
        while (i < condicion.length()
                && (Character.isDigit(condicion.charAt(i)) || (condicion.charAt(i) == '.' && !conPunto))) {
            conPunto = conPunto || condicion.charAt(i) == '.';
            i++;
        }
        simbolos.add(new Simbolo(Clase.NUMERO, condicion.substring(desde, i), desde));
        return i;
    }

    private static int leerNombre(String condicion, int desde, List<Simbolo> simbolos) {
        int i = desde;
        while (i < condicion.length() && (Character.isLetterOrDigit(condicion.charAt(i))
                || condicion.charAt(i) == '_' || condicion.charAt(i) == '.')) {
            i++;
        }
        String nombre = condicion.substring(desde, i);
        if (nombre.endsWith(".") || nombre.contains("..")) {
            throw new CondicionMalEscrita("La variable " + entreComillas(nombre) + " no esta bien escrita.");
        }
        simbolos.add(new Simbolo(Clase.NOMBRE, nombre, desde));
        return i;
    }

    private static int leerOperador(String condicion, int desde, List<Simbolo> simbolos) {
        int largo = desde + 1 < condicion.length() && condicion.charAt(desde + 1) == '=' ? 2 : 1;
        String operador = condicion.substring(desde, desde + largo);
        if (operador.equals("=") || operador.equals("!")) {
            throw new CondicionMalEscrita("Se esperaba ==, !=, >, >=, < o <=, no " + entreComillas(operador)
                    + ", en la posicion " + (desde + 1) + ".");
        }
        simbolos.add(new Simbolo(Clase.OPERADOR, operador, desde));
        return desde + largo;
    }

    /** Recorre los simbolos una sola vez y de izquierda a derecha, como los lee una persona. */
    private static final class Lector {

        private final List<Simbolo> simbolos;
        private int i;

        Lector(List<Simbolo> simbolos) {
            this.simbolos = simbolos;
        }

        void expresion() {
            termino();
            while (hay() && actual().esPalabra("or")) {
                i++;
                termino();
            }
        }

        void exigirElFinal() {
            if (hay()) {
                throw new CondicionMalEscrita("Sobra " + entreComillas(actual().texto()) + " al final de la "
                        + "condicion; una comparacion se une a la siguiente con and o con or.");
            }
        }

        private void termino() {
            factor();
            while (hay() && actual().esPalabra("and")) {
                i++;
                factor();
            }
        }

        private void factor() {
            if (hay() && actual().esPalabra("not")) {
                i++;
                factor();
            } else if (hay() && actual().es(Clase.ABRE)) {
                i++;
                expresion();
                exigir(Clase.CIERRA, "Falta cerrar el parentesis.");
            } else {
                comparacion();
            }
        }

        private void comparacion() {
            Simbolo ruta = exigir(Clase.NOMBRE, "Se esperaba el nombre de una variable, como payment.status.");
            if (PALABRAS.contains(ruta.texto())) {
                throw new CondicionMalEscrita(entreComillas(ruta.texto()) + " es una palabra de la gramatica, no el "
                        + "nombre de una variable.");
            }
            Simbolo operador = exigir(Clase.OPERADOR,
                    "Se esperaba ==, !=, >, >=, < o <= despues de " + entreComillas(ruta.texto()) + ".");
            Simbolo valor = valor(ruta);
            if (RELACIONALES.contains(operador.texto()) && !esNumeroOFecha(valor)) {
                throw new CondicionMalEscrita("Los operadores >, >=, < y <= solo comparan numeros o fechas, y "
                        + entreComillas(valor.texto()) + " no lo es.");
            }
        }

        private Simbolo valor(Simbolo ruta) {
            if (!hay() || actual().es(Clase.ABRE) || actual().es(Clase.CIERRA) || actual().es(Clase.OPERADOR)) {
                throw new CondicionMalEscrita("Falta con que comparar " + entreComillas(ruta.texto()) + ".");
            }
            return simbolos.get(i++);
        }

        private static boolean esNumeroOFecha(Simbolo valor) {
            return valor.es(Clase.NUMERO) || (valor.es(Clase.TEXTO) && esFecha(valor.texto()));
        }

        private static boolean esFecha(String texto) {
            try {
                LocalDate.parse(texto);
                return true;
            } catch (DateTimeParseException noEsUnaFecha) {
                return esFechaConHora(texto);
            }
        }

        private static boolean esFechaConHora(String texto) {
            try {
                LocalDateTime.parse(texto);
                return true;
            } catch (DateTimeParseException noEsUnaFecha) {
                return false;
            }
        }

        private Simbolo exigir(Clase clase, String queja) {
            if (!hay() || !actual().es(clase)) {
                throw new CondicionMalEscrita(queja);
            }
            return simbolos.get(i++);
        }

        private boolean hay() {
            return i < simbolos.size();
        }

        private Simbolo actual() {
            return simbolos.get(i);
        }
    }
}
