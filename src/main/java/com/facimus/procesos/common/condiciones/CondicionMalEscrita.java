package com.facimus.procesos.common.condiciones;

/**
 * Lo que se levanta cuando una condicion no se puede compilar. Su mensaje es el que el diagnostico muestra junto al
 * arco, asi que dice que se esperaba y donde, no "error de sintaxis".
 */
public class CondicionMalEscrita extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public CondicionMalEscrita(String mensaje) {
        super(mensaje);
    }
}
