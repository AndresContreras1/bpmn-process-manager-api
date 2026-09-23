package com.facimus.procesos.common;

/** 503: la funcion existe pero esta apagada en esta instalacion, porque le falta con que llamar al servicio. */
public class IntegracionNoConfiguradaException extends RuntimeException {

    public IntegracionNoConfiguradaException(String mensaje) {
        super(mensaje);
    }
}
