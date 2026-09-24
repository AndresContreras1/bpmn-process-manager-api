package com.facimus.procesos.common;

/**
 * La peticion pide algo que esta bien escrito pero que quien la manda no puede pedir, como mirar lo eliminado sin
 * ser administrador. No es un 403 de la matriz de permisos: el endpoint si es suyo, el parametro no.
 */
public class SolicitudInvalidaException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public SolicitudInvalidaException(String mensaje) {
        super(mensaje);
    }
}
