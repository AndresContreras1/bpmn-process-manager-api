package com.facimus.procesos.modelado.service.impl;

import com.facimus.procesos.common.ReglaNegocioException;

/**
 * El elemento cuyo borrado se quiere simular, tal como llega en la peticion: TIPO:id, por ejemplo GATEWAY:12. Se
 * escribe asi, y no con dos parametros, porque el editor lo manda de una pieza cuando alguien pulsa el boton de
 * borrar y todavia no ha confirmado.
 */
record ElementoDelDiagrama(Tipo tipo, Long id) {

    private static final String COMO_SE_ESCRIBE = "El elemento se escribe TIPO:id, por ejemplo GATEWAY:12.";

    enum Tipo {
        POOL,
        LANE,
        ACTIVIDAD,
        GATEWAY,
        EVENTO,
        ARCO,
        MENSAJE
    }

    static ElementoDelDiagrama de(String texto) {
        String[] partes = texto.split(":", 2);
        if (partes.length != 2) {
            throw new ReglaNegocioException(COMO_SE_ESCRIBE);
        }
        try {
            return new ElementoDelDiagrama(Tipo.valueOf(partes[0].trim().toUpperCase()),
                    Long.valueOf(partes[1].trim()));
        } catch (IllegalArgumentException malEscrito) {
            throw new ReglaNegocioException(COMO_SE_ESCRIBE);
        }
    }

    /** Como se escribe de vuelta en la respuesta, ya normalizado. */
    String texto() {
        return tipo + ":" + id;
    }

    boolean esNodo() {
        return tipo == Tipo.ACTIVIDAD || tipo == Tipo.GATEWAY || tipo == Tipo.EVENTO;
    }
}
