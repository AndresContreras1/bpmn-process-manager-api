package com.facimus.procesos.common;

import java.util.Map;

/** PR 40: lo que dice {@code If-Match} ya no es lo que hay: 412, con lo necesario para decidir. */
public class PrecondicionFallidaException extends RuntimeException {

    private final transient Map<String, Object> detalle;

    public PrecondicionFallidaException(String mensaje, Map<String, Object> detalle) {
        super(mensaje);
        this.detalle = Map.copyOf(detalle);
    }

    public Map<String, Object> getDetalle() {
        return detalle;
    }
}
