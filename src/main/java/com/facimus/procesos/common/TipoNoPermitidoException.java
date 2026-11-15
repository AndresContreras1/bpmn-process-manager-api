package com.facimus.procesos.common;

/** PR 61: un archivo cuyo contenido no es de un tipo de la lista blanca, se llame como se llame. Responde 415. */
public class TipoNoPermitidoException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public TipoNoPermitidoException(String mensaje) {
        super(mensaje);
    }
}
