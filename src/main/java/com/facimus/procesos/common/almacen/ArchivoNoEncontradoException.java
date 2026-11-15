package com.facimus.procesos.common.almacen;

/** PR 61: una clave que el almacen no tiene. La base dice que existe y el almacen no: eso es un error de operacion. */
public class ArchivoNoEncontradoException extends RuntimeException {

    public ArchivoNoEncontradoException(String clave) {
        super("El almacen no tiene " + clave);
    }
}
