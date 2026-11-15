package com.facimus.procesos.common.trabajos;

/**
 * Un trabajo que no pudo empezar por algo ajeno a el, como que no haya con que hacerlo ahora: no fallo. La cola lo
 * vuelve a poner en espera la espera base, sin gastarle un intento, con el motivo como su ultimo error.
 */
public class TrabajoAplazadoException extends RuntimeException {

    public TrabajoAplazadoException(String mensaje) {
        super(mensaje);
    }
}
