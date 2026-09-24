package com.facimus.procesos.common;

/**
 * Se lanza cuando quien pide algo no tiene permiso para hacerlo y el motivo es de la tienda, no de la matriz fija de
 * roles: la politica de estructura, por ejemplo. La matriz fija la resuelve Spring Security antes de llegar aqui.
 */
public class SinPermisoException extends RuntimeException {

    public SinPermisoException(String mensaje) {
        super(mensaje);
    }
}
