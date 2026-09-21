package com.facimus.procesos.common;

/**
 * Se lanza cuando se busca una entidad por id + empresaId y no existe
 * (o pertenece a otra empresa, lo cual para efectos de aislamiento es
 * indistinguible de "no existe").
 */
public class RecursoNoEncontradoException extends RuntimeException {

    public RecursoNoEncontradoException(String mensaje) {
        super(mensaje);
    }
}
