package com.facimus.procesos.common;

/** PR 61: un archivo que pasa el tamano que se acepta. Responde 413 sin guardar nada. */
public class ArchivoDemasiadoGrandeException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ArchivoDemasiadoGrandeException(String mensaje) {
        super(mensaje);
    }
}
