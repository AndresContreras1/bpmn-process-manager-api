package com.facimus.procesos.common.condiciones;

import java.util.Map;
import java.util.Optional;

/**
 * De donde saca una condicion el valor de una variable. Es una interfaz y no un mapa para que quien evalua decida
 * como se leen las rutas y que hacer con las que no existen: el motor anota las ausentes en la bitacora del caso.
 */
public interface Variables {

    /** El valor de una ruta como {@code payment.status}, o vacio si el caso no la tiene. */
    Optional<Object> valor(String ruta);

    /** Se avisa una vez por cada comparacion que no encontro su variable. Por defecto no interesa a nadie. */
    default void anotarAusente(String ruta) {
        // Quien necesite saberlo lo sobrescribe.
    }

    /**
     * Las variables de un mapa anidado, como el que sale de un JSON: {@code payment.status} baja por
     * {@code payment} y despues busca {@code status}. Una ruta que atraviesa algo que no es un mapa no existe.
     */
    static Variables de(Map<String, Object> mapa) {
        return ruta -> buscar(mapa, ruta);
    }

    private static Optional<Object> buscar(Map<String, Object> mapa, String ruta) {
        Object actual = mapa;
        for (String tramo : ruta.split("\\.")) {
            if (!(actual instanceof Map<?, ?> nivel)) {
                return Optional.empty();
            }
            actual = nivel.get(tramo);
        }
        return Optional.ofNullable(actual);
    }
}
