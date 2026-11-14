package com.facimus.procesos.common.formularios;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;

import com.google.re2j.Pattern;
import com.google.re2j.PatternSyntaxException;

/**
 * PR 69: el patron de un campo lo escribe un usuario, asi que no puede colgar el servidor. Se evalua con RE2/J, que
 * recorre el texto en tiempo lineal: no tiene vuelta atras, y por eso tampoco referencias hacia atras ni miradas
 * alrededor, que RE2/J rechaza al compilar.
 *
 * <p>Lo unico caro que le queda a RE2/J es compilar repeticiones contadas anidadas: {@code ((a{1000}){1000}){1000}}
 * arma un programa de mil millones de pasos y se come la memoria antes de responder. Por eso, antes de compilar, se
 * multiplican las repeticiones de cada nivel y se rechaza lo que pase de {@link #REPETICIONES_MAXIMAS}, como hace
 * RE2 en C++. Con eso y un patron corto, compilar y evaluar son baratos siempre.
 */
public final class Patrones {

    /** Lo mas largo que se acepta escribir. */
    public static final int LARGO_MAXIMO = 200;

    /** Cuantas veces se puede repetir algo, sumando lo que se anida. */
    static final int REPETICIONES_MAXIMAS = 1_000;

    private static final String CITA = "\\Q";
    private static final String FIN_DE_CITA = "\\E";

    private Patrones() {
    }

    /** Por que no sirve el patron, o vacio si sirve. */
    public static Optional<String> problema(String patron) {
        if (patron.length() > LARGO_MAXIMO) {
            return Optional.of("El patron no puede superar " + LARGO_MAXIMO + " caracteres.");
        }
        if (repeticiones(patron) > REPETICIONES_MAXIMAS) {
            return Optional.of("El patron repite demasiado: las repeticiones contadas, multiplicadas por las que las "
                    + "contienen, no pueden pasar de " + REPETICIONES_MAXIMAS + ".");
        }
        try {
            Pattern.compile(patron);
            return Optional.empty();
        } catch (PatternSyntaxException malo) {
            return Optional.of("El patron no es una expresion regular RE2: " + malo.getDescription() + ".");
        }
    }

    /** Si el texto entero cumple el patron. El patron ya paso {@link #problema(String)}. */
    public static boolean cumple(String patron, String texto) {
        return Pattern.compile(patron).matches(texto);
    }

    /**
     * La repeticion contada mas grande del patron, con las de los grupos que la contienen multiplicadas: en
     * {@code (a{10}b){20}}, doscientas. Se lee como texto, sin compilar: un escape salta el caracter que sigue, una
     * clase entre corchetes es un solo atomo y {@code {n}}, {@code {n,}} y {@code {n,m}} multiplican lo de antes por
     * el mayor de sus numeros. Lo que no sea una repeticion bien escrita no multiplica: RE2/J lo trata como texto o
     * lo rechaza al compilar.
     *
     * <p>Lo que va entre {@code \Q} y {@code \E} es texto. Lo que no deja nada que repetir, un grupo de banderas como
     * {@code (?i)} o un {@code \Q\E} vacio, no cambia a que se aplica la repeticion que sigue: RE2/J la pega a lo de
     * antes, y contarla como de un atomo suelto dejaria pasar {@code (a{1000})(?i){1000}}.
     */
    static long repeticiones(String patron) {
        Lectura lectura = new Lectura(patron);
        while (lectura.quedaPatron()) {
            lectura.avanzar();
        }
        return lectura.mayor();
    }

    /**
     * Un recorrido del patron de izquierda a derecha: en que posicion va, cuanto repite el ultimo atomo y, por cada
     * grupo abierto, la repeticion mas grande que tiene dentro.
     */
    private static final class Lectura {

        private final String patron;
        private final Deque<long[]> niveles = new ArrayDeque<>();
        private long ultimo = 1;
        private int posicion;

        Lectura(String patron) {
            this.patron = patron;
            niveles.push(new long[] {1});
        }

        boolean quedaPatron() {
            return posicion < patron.length();
        }

        /** Lee lo que empieza en la posicion actual y lo anota en el grupo que lo contiene. */
        void avanzar() {
            if (patron.startsWith(CITA, posicion)) {
                leerCita();
            } else {
                switch (patron.charAt(posicion)) {
                    case '\\' ->atomoHasta(posicion + 2);
                    case '[' -> atomoHasta(finDeLaClase(patron, posicion));
                    case '(' -> abrirGrupo();
                    case ')' -> cerrarGrupo();
                    case '{' -> leerRepeticion();
                    default -> atomoHasta(posicion + 1);
                }
            }
            long[] nivel = niveles.peek();
            nivel[0] = Math.max(nivel[0], ultimo);
        }

        /** Entre {@code \Q} y {@code \E} cada caracter es texto, parentesis incluidos; sin texto no deja nada que repetir. */
        private void leerCita() {
            int fin = patron.indexOf(FIN_DE_CITA, posicion + 2);
            int hasta = fin < 0 ? patron.length() : fin;
            ultimo = hasta > posicion + 2 ? 1 : ultimo;
            posicion = fin < 0 ? patron.length() : fin + 2;
        }

        /** Un atomo suelto, que se repite una vez, termina justo antes de {@code fin}. */
        private void atomoHasta(int fin) {
            ultimo = 1;
            posicion = fin;
        }

        /** {@code (?i)} solo cambia banderas: tampoco deja nada que repetir. Otro parentesis abre un grupo. */
        private void abrirGrupo() {
            int banderas = finDeLasBanderas(patron, posicion);
            if (banderas > 0) {
                posicion = banderas;
            } else {
                ultimo = 1;
                niveles.push(new long[] {1});
                posicion++;
            }
        }

        private void cerrarGrupo() {
            ultimo = niveles.size() > 1 ? niveles.pop()[0] : 1;
            posicion++;
        }

        private void leerRepeticion() {
            int cierre = patron.indexOf('}', posicion);
            long veces = cierre < 0 ? -1 : veces(patron.substring(posicion + 1, cierre));
            if (veces < 0) {
                atomoHasta(posicion + 1);
            } else {
                // Con el tope basta saber que se paso: asi el producto nunca se desborda
                ultimo = Math.min(ultimo * veces, REPETICIONES_MAXIMAS + 1L);
                posicion = cierre + 1;
            }
        }

        /** La repeticion mas grande de cada grupo que quedo abierto, el de afuera incluido. */
        long mayor() {
            long mayor = 1;
            for (long[] nivel : niveles) {
                mayor = Math.max(mayor, nivel[0]);
            }
            return mayor;
        }

        /** Donde termina un grupo de solo banderas, como {@code (?i)} o {@code (?s-m)}; -1 si no es uno. */
        private static int finDeLasBanderas(String patron, int desde) {
            int i = desde + 1;
            if (i >= patron.length() || patron.charAt(i) != '?') {
                return -1;
            }
            i++;
            while (i < patron.length() && (Character.isLetter(patron.charAt(i)) || patron.charAt(i) == '-')) {
                i++;
            }
            return i < patron.length() && patron.charAt(i) == ')' ? i + 1 : -1;
        }

        /** Donde termina una clase entre corchetes: un {@code ]} justo al abrir, o tras {@code ^}, es parte de ella. */
        private static int finDeLaClase(String patron, int desde) {
            int i = desde + 1;
            if (i < patron.length() && patron.charAt(i) == '^') {
                i++;
            }
            if (i < patron.length() && patron.charAt(i) == ']') {
                i++;
            }
            while (i < patron.length() && patron.charAt(i) != ']') {
                i += patron.charAt(i) == '\\' ? 2 : 1;
            }
            return i + 1;
        }

        /** El mayor numero de {@code n}, {@code n,} o {@code n,m}; -1 si no es una repeticion. */
        private static long veces(String dentro) {
            int coma = dentro.indexOf(',');
            String desde = coma < 0 ? dentro : dentro.substring(0, coma);
            String hasta = coma < 0 ? "" : dentro.substring(coma + 1);
            if (!soloDigitos(desde) || desde.isEmpty() || !soloDigitos(hasta) || desde.length() > 6
                    || hasta.length() > 6) {
                return -1;
            }
            long menor = Long.parseLong(desde);
            return hasta.isEmpty() ? menor : Math.max(menor, Long.parseLong(hasta));
        }

        private static boolean soloDigitos(String texto) {
            return texto.chars().allMatch(c -> c >= '0' && c <= '9');
        }
    }
}
