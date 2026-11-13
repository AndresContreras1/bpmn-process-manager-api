package com.facimus.procesos.ejecucion.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.facimus.procesos.gestion.service.LecturaDeFilasDePrueba;
import com.facimus.procesos.gestion.service.PaqueteEnMemoria;

/**
 * PR 66: lo que ejecucion pone en la exportacion: los casos en JSON y en CSV con las mismas columnas, y en JSON sus
 * pasos, su historia y sus mensajes, cada tabla recorrida de a pedazos.
 */
class ExportacionDeCasosTest {

    private static final Long EMPRESA = 1L;

    private final LecturaDeFilasDePrueba lector = new LecturaDeFilasDePrueba();
    private final PaqueteEnMemoria paquete = new PaqueteEnMemoria();

    private static Map<String, Object> caso(long id, String referencia) {
        Map<String, Object> caso = new LinkedHashMap<>();
        caso.put("id", id);
        caso.put("procesoId", 3L);
        caso.put("versionDelProceso", 2);
        caso.put("referencia", referencia);
        caso.put("estado", "EN_CURSO");
        caso.put("tickInicio", 0);
        caso.put("tickFin", null);
        caso.put("fechaCreacion", "2026-11-12T10:00");
        caso.put("fechaFin", null);
        caso.put("variables", "{\"orderId\":\"ORD-1\"}");
        return caso;
    }

    @Test
    @DisplayName("los casos salen en JSON y en CSV, con las columnas del JSON en su orden, y sus pasos, su historia "
            + "y sus mensajes en JSON")
    void casos() {
        lector.con(ExportacionDeCasos.CASOS, List.of(caso(1, "ORD-1"), caso(2, "=cmd")), EMPRESA)
                .con(ExportacionDeCasos.ACTIVIDADES, List.of(Map.of("id", 10L)), EMPRESA)
                .con(ExportacionDeCasos.EVENTOS, List.of(Map.of("id", 20L)), EMPRESA)
                .con(ExportacionDeCasos.MENSAJES_ENTRANTES, List.of(Map.of("id", 30L)), EMPRESA)
                .con(ExportacionDeCasos.MENSAJES_SALIENTES, List.of(Map.of("id", 40L)), EMPRESA);

        new ExportacionDeCasos(lector).escribir(EMPRESA, paquete);

        assertThat(paquete.archivos().keySet()).containsExactly("casos/casos.json", "casos/casos.csv",
                "casos/actividades.json", "casos/eventos.json", "casos/mensajes-entrantes.json",
                "casos/mensajes-salientes.json");
        assertThat(paquete.filas("casos/casos.json")).containsExactly(caso(1, "ORD-1"), caso(2, "=cmd"));
        assertThat(paquete.filas("casos/casos.csv")).containsExactly(ExportacionDeCasos.COLUMNAS_DE_CASOS,
                Arrays.asList(1L, 3L, 2, "ORD-1", "EN_CURSO", 0, null, "2026-11-12T10:00", null,
                        "{\"orderId\":\"ORD-1\"}"),
                Arrays.asList(2L, 3L, 2, "=cmd", "EN_CURSO", 0, null, "2026-11-12T10:00", null,
                        "{\"orderId\":\"ORD-1\"}"));
        assertThat(paquete.filas("casos/actividades.json")).containsExactly(Map.of("id", 10L));
        assertThat(paquete.filas("casos/eventos.json")).containsExactly(Map.of("id", 20L));
        assertThat(paquete.filas("casos/mensajes-entrantes.json")).containsExactly(Map.of("id", 30L));
        assertThat(paquete.filas("casos/mensajes-salientes.json")).containsExactly(Map.of("id", 40L));
        assertThat(lector.cursores().values()).containsOnly("id");
    }

    @Test
    @DisplayName("las columnas del CSV son las claves del JSON de cada caso, sin la version de la fila")
    void columnas() {
        assertThat(ExportacionDeCasos.COLUMNAS_DE_CASOS).containsExactlyElementsOf(caso(1, null).keySet());
        assertThat(ExportacionDeCasos.CASOS).contains("v.numero as version_del_proceso").doesNotContain("c.version,");
    }

    @Test
    @DisplayName("sin casos, cada archivo sale igual: vacio, y el CSV con su cabecera")
    void sinCasos() {
        new ExportacionDeCasos(lector).escribir(EMPRESA, paquete);

        assertThat(paquete.filas("casos/casos.json")).isEmpty();
        assertThat(paquete.filas("casos/casos.csv")).containsExactly(ExportacionDeCasos.COLUMNAS_DE_CASOS);
    }
}
