package com.facimus.procesos.common.trabajos;

/**
 * Un trabajo tomado de la cola: de quien es, de que tipo, con que datos, y en que intento va.
 *
 * @param empresaId la tienda del trabajo, o {@code null} si es del sistema
 * @param datos lo que el manejador necesita, por lo general en JSON
 */
public record Trabajo(long id, Long empresaId, String tipo, String datos, int intento, int maximoIntentos) {
}
