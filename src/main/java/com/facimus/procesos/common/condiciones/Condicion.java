package com.facimus.procesos.common.condiciones;

import java.util.Optional;

/**
 * Una condicion ya compilada: el arbol de lo que estaba escrito en el arco, listo para responder si se cumple con
 * las variables de un caso. No ejecuta nada ni llama a nada, solo lee variables y compara.
 *
 * <p>El arbol se arma una sola vez, al publicar o al construir el grafo de la version, y se evalua tantas veces
 * como casos pasen por ese gateway.
 */
public sealed interface Condicion {

    boolean seCumple(Variables variables);

    /** Como se escribio, para que la bitacora del caso diga que condicion se tomo. */
    String texto();

    /** {@code izquierda and derecha}. */
    record Y(Condicion izquierda, Condicion derecha) implements Condicion {

        /**
         * Las dos ramas se evaluan siempre. Cortar en cuanto la primera es falsa daria el mismo resultado, pero
         * dejaria sin anotar las variables que le faltan a la segunda, que es lo que explica por que un caso se
         * quedo sin camino.
         */
        @Override
        public boolean seCumple(Variables variables) {
            boolean primera = izquierda.seCumple(variables);
            boolean segunda = derecha.seCumple(variables);
            return primera && segunda;
        }

        @Override
        public String texto() {
            return izquierda.texto() + " and " + derecha.texto();
        }
    }

    /** {@code izquierda or derecha}. */
    record O(Condicion izquierda, Condicion derecha) implements Condicion {

        @Override
        public boolean seCumple(Variables variables) {
            boolean primera = izquierda.seCumple(variables);
            boolean segunda = derecha.seCumple(variables);
            return primera || segunda;
        }

        @Override
        public String texto() {
            return izquierda.texto() + " or " + derecha.texto();
        }
    }

    /** {@code not condicion}. */
    record No(Condicion condicion) implements Condicion {

        @Override
        public boolean seCumple(Variables variables) {
            return !condicion.seCumple(variables);
        }

        @Override
        public String texto() {
            return "not " + condicion.texto();
        }
    }

    /** Un grupo entre parentesis: no cambia lo que vale, pero conserva como se escribio. */
    record Grupo(Condicion condicion) implements Condicion {

        @Override
        public boolean seCumple(Variables variables) {
            return condicion.seCumple(variables);
        }

        @Override
        public String texto() {
            return "(" + condicion.texto() + ")";
        }
    }

    /**
     * {@code ruta operador literal}, la unica hoja del arbol. El literal ya viene con su tipo: un
     * {@link java.math.BigDecimal}, un {@link Boolean} o un texto.
     */
    record Comparacion(String ruta, Operador operador, Object literal, String comoSeEscribio) implements Condicion {

        /**
         * Una variable que el caso no tiene hace falsa la comparacion, se escriba como se escriba, y deja aviso:
         * un gateway sin salida por defecto acabara sin camino y hay que poder decir por que.
         */
        @Override
        public boolean seCumple(Variables variables) {
            Optional<Object> valor = variables.valor(ruta);
            if (valor.isEmpty()) {
                variables.anotarAusente(ruta);
                return false;
            }
            return operador.compara(valor.orElseThrow(), literal);
        }

        @Override
        public String texto() {
            return ruta + " " + operador.simbolo() + " " + comoSeEscribio;
        }
    }
}
