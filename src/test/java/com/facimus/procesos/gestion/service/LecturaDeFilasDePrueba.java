package com.facimus.procesos.gestion.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * PR 66: las filas de mentira para las pruebas de cada parte de la exportacion. Cada consulta, con sus parametros,
 * devuelve las filas que la prueba le dio; una que no tiene ninguna no devuelve nada. Anota por que columna se
 * recorrio cada tabla.
 */
public class LecturaDeFilasDePrueba implements LecturaDeFilas {

    private final Map<String, List<Map<String, Object>>> respuestas = new HashMap<>();
    private final Map<String, String> cursores = new LinkedHashMap<>();
    private final List<String> consultas = new ArrayList<>();

    /** Lo que devuelve esa consulta con esos parametros (los de {@link #recorrer}: solo la tienda). */
    public LecturaDeFilasDePrueba con(String consulta, List<Map<String, Object>> filas, Object... parametros) {
        respuestas.put(clave(consulta, parametros), filas);
        return this;
    }

    /** La columna por la que se recorrio cada consulta. */
    public Map<String, String> cursores() {
        return cursores;
    }

    /** Cada consulta en el orden en que se hizo. */
    public List<String> consultas() {
        return consultas;
    }

    @Override
    public void recorrer(String consulta, Long empresaId, String cursor, Consumer<Map<String, Object>> cadaFila) {
        consultas.add(consulta);
        cursores.put(consulta, cursor);
        respuestas.getOrDefault(clave(consulta, new Object[] {empresaId}), List.of()).forEach(cadaFila);
    }

    @Override
    public List<Map<String, Object>> filas(String consulta, Object... parametros) {
        consultas.add(consulta);
        // Una copia de cada fila: quien las lee puede quitarles columnas, como hace la de verdad con las suyas
        return respuestas.getOrDefault(clave(consulta, parametros), List.of()).stream()
                .map(fila -> (Map<String, Object>) new LinkedHashMap<>(fila)).toList();
    }

    private static String clave(String consulta, Object[] parametros) {
        return consulta + Arrays.toString(parametros);
    }
}
