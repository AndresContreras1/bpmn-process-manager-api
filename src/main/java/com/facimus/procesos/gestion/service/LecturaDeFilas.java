package com.facimus.procesos.gestion.service;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * PR 66: las filas que una parte de la exportacion lee con su SQL, como mapas listos para JSON. Las columnas salen
 * solo si la consulta las nombra, que es como cada parte deja fuera lo que no se exporta (las claves, los secretos);
 * sus nombres pasan a camelCase, como en la API, las fechas a ISO 8601 en UTC y los arreglos a listas.
 */
public interface LecturaDeFilas {

    /** Cuantas filas trae cada vuelta de {@link #recorrer}: ni una tabla grande entra entera en memoria. */
    int POR_VUELTA = 500;

    /**
     * Recorre una tabla de la tienda por su id, de a {@link #POR_VUELTA} filas. La consulta toma la tienda, el ultimo
     * id visto y el tope, en ese orden, y ordena por id:
     * {@code ... where empresa_id = ? and id > ? order by id limit ?}.
     */
    default void recorrer(String consulta, Long empresaId, Consumer<Map<String, Object>> cadaFila) {
        recorrer(consulta, empresaId, "id", cadaFila);
    }

    /** Como el otro, por otra columna que crece, como la secuencia de la bitacora. */
    void recorrer(String consulta, Long empresaId, String cursor, Consumer<Map<String, Object>> cadaFila);

    /** Las filas de una consulta chica, enteras: las de un proceso, las de un documento. */
    List<Map<String, Object>> filas(String consulta, Object... parametros);
}
