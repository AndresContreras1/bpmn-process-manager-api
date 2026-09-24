package com.facimus.procesos.common;

import java.util.List;

/**
 * Se lanza cuando una operacion viola una regla de negocio del dominio
 * (ej. NIT duplicado, arco entre pools distintos, rol en uso, etc.).
 * El controlador o el manejador global la traduce a un mensaje de error.
 */
public class ReglaNegocioException extends RuntimeException {

    /** Lo que hay que arreglar, cuando son varias cosas: el mensaje dice cuantas y esta lista, cuales. */
    private final transient List<String> errores;

    public ReglaNegocioException(String mensaje) {
        this(mensaje, List.of());
    }

    public ReglaNegocioException(String mensaje, List<String> errores) {
        super(mensaje);
        this.errores = List.copyOf(errores);
    }

    public List<String> getErrores() {
        return errores;
    }
}
