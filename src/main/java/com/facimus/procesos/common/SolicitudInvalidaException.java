package com.facimus.procesos.common;

import java.util.Map;
import java.util.TreeMap;

/**
 * La peticion pide algo que esta bien escrito pero que quien la manda no puede pedir, como mirar lo eliminado sin
 * ser administrador. No es un 403 de la matriz de permisos: el endpoint si es suyo, el parametro no.
 *
 * <p>PR 63: cuando son varios campos, como las menciones de un comentario, dice cual con su motivo, como la
 * validacion.
 */
public class SolicitudInvalidaException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** Campo y motivo, en orden. */
    private final transient Map<String, String> errores;

    public SolicitudInvalidaException(String mensaje) {
        this(mensaje, Map.of());
    }

    public SolicitudInvalidaException(String mensaje, Map<String, String> errores) {
        super(mensaje);
        this.errores = new TreeMap<>(errores);
    }

    public Map<String, String> getErrores() {
        return errores;
    }
}
