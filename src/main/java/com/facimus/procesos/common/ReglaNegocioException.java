package com.facimus.procesos.common;

/**
 * Se lanza cuando una operacion viola una regla de negocio del dominio
 * (ej. NIT duplicado, arco entre pools distintos, rol en uso, etc.).
 * El controlador o el manejador global la traduce a un mensaje de error.
 */
public class ReglaNegocioException extends RuntimeException {

    public ReglaNegocioException(String mensaje) {
        super(mensaje);
    }
}
