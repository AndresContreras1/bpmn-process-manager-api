package com.facimus.procesos.common.tiempo;

/**
 * PR 68: lo que se levanta cuando la expresion de un temporizador no se entiende. Su mensaje es el que el diagnostico
 * muestra junto al evento, asi que dice que se esperaba.
 */
public class ExpresionMalEscrita extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ExpresionMalEscrita(String mensaje) {
        super(mensaje);
    }
}
