package com.facimus.procesos.ejecucion.service.impl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.facimus.procesos.common.condiciones.Variables;
import com.facimus.procesos.ejecucion.model.Caso;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Las variables de un caso mientras el motor trabaja con el: lo que se le paso al abrirlo, los cuerpos de los
 * mensajes recibidos y los datos de las tareas completadas, mas {@code caso.referencia} y {@code caso.tick}, que no
 * se guardan porque el caso ya los tiene en sus columnas.
 *
 * <p>Anota las rutas que una condicion pidio y el caso no tiene. Esa lista es la que acaba en la bitacora como
 * VARIABLE_AUSENTE y explica despues por que un gateway se quedo sin camino.
 */
final class VariablesDelCaso implements Variables {

    private static final TypeReference<Map<String, Object>> MAPA = new TypeReference<>() {
    };
    private static final String DEL_CASO = "caso";

    private final Map<String, Object> guardadas;
    private final Map<String, Object> delCaso;
    private final List<String> ausentes = new ArrayList<>();
    private final JsonMapper json;

    private VariablesDelCaso(Map<String, Object> guardadas, Map<String, Object> delCaso, JsonMapper json) {
        this.guardadas = guardadas;
        this.delCaso = delCaso;
        this.json = json;
    }

    static VariablesDelCaso de(Caso caso, JsonMapper json) {
        Map<String, Object> guardadas = new LinkedHashMap<>(json.readValue(caso.getVariables(), MAPA));
        Map<String, Object> delCaso = new LinkedHashMap<>();
        delCaso.put("referencia", caso.getReferencia());
        delCaso.put("tick", caso.getTickInicio());
        return new VariablesDelCaso(guardadas, delCaso, json);
    }

    @Override
    public Optional<Object> valor(String ruta) {
        String[] tramos = ruta.split("\\.");
        int desde = DEL_CASO.equals(tramos[0]) ? 1 : 0;
        Object actual = desde == 1 ? delCaso : guardadas;
        for (int i = desde; i < tramos.length; i++) {
            if (!(actual instanceof Map<?, ?> nivel)) {
                return Optional.empty();
            }
            actual = nivel.get(tramos[i]);
        }
        return Optional.ofNullable(actual);
    }

    @Override
    public void anotarAusente(String ruta) {
        if (!ausentes.contains(ruta)) {
            ausentes.add(ruta);
        }
    }

    /** Las rutas que alguna condicion pidio y no estaban, en el orden en que se pidieron. */
    List<String> ausentes() {
        return List.copyOf(ausentes);
    }

    void olvidarAusentes() {
        ausentes.clear();
    }

    /** Mete un grupo de valores bajo un nombre, como el cuerpo de un mensaje o los datos de una tarea. */
    void poner(String nombre, Object valor) {
        guardadas.put(nombre, valor);
    }

    /** Mete los datos de una tarea bajo {@code tarea.<nombre en camello>}, como dice la semantica. */
    @SuppressWarnings("unchecked")
    void ponerDeLaTarea(String nombreDelNodo, Map<String, Object> datos) {
        Object tareas = guardadas.computeIfAbsent("tarea", sinTareas -> new LinkedHashMap<String, Object>());
        if (tareas instanceof Map<?, ?> mapa) {
            ((Map<String, Object>) mapa).put(enCamello(nombreDelNodo), datos);
        }
    }

    /** Lo que se guarda en la columna: sin lo del caso, que se lee de sus columnas cada vez. */
    String comoJson() {
        return json.writeValueAsString(guardadas);
    }

    /**
     * "Pick and pack items" se convierte en "pickAndPackItems": el nombre del nodo tal cual tiene espacios y no se
     * podria escribir en una condicion.
     */
    static String enCamello(String nombre) {
        String[] palabras = nombre.trim().split("[^\\p{Alnum}]+");
        StringBuilder camello = new StringBuilder();
        for (String palabra : palabras) {
            if (palabra.isEmpty()) {
                continue;
            }
            if (camello.isEmpty()) {
                camello.append(palabra.substring(0, 1).toLowerCase()).append(palabra.substring(1));
            } else {
                camello.append(palabra.substring(0, 1).toUpperCase()).append(palabra.substring(1));
            }
        }
        return camello.toString();
    }
}
