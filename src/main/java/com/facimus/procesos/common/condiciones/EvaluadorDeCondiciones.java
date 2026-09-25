package com.facimus.procesos.common.condiciones;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * D6: el lenguaje en el que se escriben las condiciones de un gateway, compilado a mano. Una condicion compara el
 * valor de una variable del caso con un valor fijo, y esas comparaciones se combinan con and, or y not:
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
 * <p>No hay funciones, ni asignaciones, ni llamadas, ni acceso a nada que no sean las variables del caso: lo que se
 * escribe en un arco lo escribe un usuario, y un lenguaje que ejecuta codigo seria una puerta abierta. Por eso no
 * se usa SpEL ni un motor de scripts, y una regla de ArchUnit lo deja escrito.
 *
 * <p>Se usa en dos momentos. Al publicar, {@link #problema(String)} dice por que una condicion no compila y el
 * diagnostico lo muestra como E-08. Al ejecutar, {@link #compilar(String)} devuelve el arbol que el motor evalua
 * contra las variables del caso. Es el mismo analisis: una condicion que se publica es una condicion que corre.
 *
 * <p>Vive en {@code common} y no en {@code ejecucion} porque lo necesitan los dos lados: el diagnostico, que es de
 * {@code modelado}, y el motor. Tenerlo en cualquiera de ellos pondria a un modulo a mirar hacia arriba.
 */
public final class EvaluadorDeCondiciones {

    private static final Set<String> PALABRAS = Set.of("and", "or", "not", "true", "false");
    private static final String OPERADORES = "=!<>";

    private EvaluadorDeCondiciones() {
    }

    /**
     * La condicion compilada, lista para evaluarse.
     *
     * @throws CondicionMalEscrita si no compila; al ejecutar no deberia pasar, porque publicar ya lo comprobo
     */
    public static Condicion compilar(String condicion) {
        Lector lector = new Lector(partir(condicion));
        Condicion arbol = lector.expresion();
        lector.exigirElFinal();
        return arbol;
    }

    /** Por que la condicion no compila, o vacio cuando esta bien escrita. */
    public static Optional<String> problema(String condicion) {
        try {
            compilar(condicion);
            return Optional.empty();
        } catch (CondicionMalEscrita mala) {
            return Optional.of(mala.getMessage());
        }
    }

    /** Lo que un mensaje cita de la condicion va entre comillas, como en el resto de las reglas. */
    private static String entreComillas(String texto) {
        return "\"" + texto + "\"";
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
        if (Operador.delSimbolo(operador).isEmpty()) {
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

        /** {@code or} une lo que {@code and} ya agrupo: por eso queda arriba y ata menos. */
        Condicion expresion() {
            Condicion condicion = termino();
            while (hay() && actual().esPalabra("or")) {
                i++;
                condicion = new Condicion.O(condicion, termino());
            }
            return condicion;
        }

        void exigirElFinal() {
            if (hay()) {
                throw new CondicionMalEscrita("Sobra " + entreComillas(actual().texto()) + " al final de la "
                        + "condicion; una comparacion se une a la siguiente con and o con or.");
            }
        }

        private Condicion termino() {
            Condicion condicion = factor();
            while (hay() && actual().esPalabra("and")) {
                i++;
                condicion = new Condicion.Y(condicion, factor());
            }
            return condicion;
        }

        private Condicion factor() {
            if (hay() && actual().esPalabra("not")) {
                i++;
                return new Condicion.No(factor());
            }
            if (hay() && actual().es(Clase.ABRE)) {
                i++;
                Condicion dentro = expresion();
                exigir(Clase.CIERRA, "Falta cerrar el parentesis.");
                return new Condicion.Grupo(dentro);
            }
            return comparacion();
        }

        private Condicion comparacion() {
            Simbolo ruta = exigir(Clase.NOMBRE, "Se esperaba el nombre de una variable, como payment.status.");
            if (PALABRAS.contains(ruta.texto())) {
                throw new CondicionMalEscrita(entreComillas(ruta.texto()) + " es una palabra de la gramatica, no el "
                        + "nombre de una variable.");
            }
            Simbolo simbolo = exigir(Clase.OPERADOR,
                    "Se esperaba ==, !=, >, >=, < o <= despues de " + entreComillas(ruta.texto()) + ".");
            Operador operador = Operador.delSimbolo(simbolo.texto()).orElseThrow();
            Simbolo valor = valor(ruta);
            if (operador.ordena() && !esNumeroOFecha(valor)) {
                throw new CondicionMalEscrita("Los operadores >, >=, < y <= solo comparan numeros o fechas, y "
                        + entreComillas(valor.texto()) + " no lo es.");
            }
            return new Condicion.Comparacion(ruta.texto(), operador, literal(valor), comoSeEscribio(valor));
        }

        private Simbolo valor(Simbolo ruta) {
            if (!hay() || actual().es(Clase.ABRE) || actual().es(Clase.CIERRA) || actual().es(Clase.OPERADOR)) {
                throw new CondicionMalEscrita("Falta con que comparar " + entreComillas(ruta.texto()) + ".");
            }
            return simbolos.get(i++);
        }

        /**
         * Un nombre sin comillas a la derecha es una constante que se compara como texto: {@code APPROVED} y
         * {@code 'APPROVED'} son lo mismo, que es como estaban escritas las condiciones antes de que corrieran.
         */
        private static Object literal(Simbolo valor) {
            if (valor.es(Clase.NUMERO)) {
                return new BigDecimal(valor.texto());
            }
            if (valor.es(Clase.NOMBRE) && (valor.texto().equals("true") || valor.texto().equals("false"))) {
                return Boolean.valueOf(valor.texto());
            }
            return valor.texto();
        }

        private static String comoSeEscribio(Simbolo valor) {
            return valor.es(Clase.TEXTO) ? "'" + valor.texto() + "'" : valor.texto();
        }

        private static boolean esNumeroOFecha(Simbolo valor) {
            return valor.es(Clase.NUMERO) || (valor.es(Clase.TEXTO) && Operador.comoFecha(valor.texto()).isPresent());
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
