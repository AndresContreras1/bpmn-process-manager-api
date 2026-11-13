package com.facimus.procesos.ejecucion.service.impl;

import java.util.List;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.facimus.procesos.gestion.service.LecturaDeFilas;
import com.facimus.procesos.gestion.service.Paquete;
import com.facimus.procesos.gestion.service.Paquete.FilasCsv;
import com.facimus.procesos.gestion.service.Paquete.FilasJson;
import com.facimus.procesos.gestion.service.ParteDeLaExportacion;

/**
 * PR 66: lo que ejecucion pone en la exportacion: los casos en JSON y en CSV, para abrirlos en una hoja de calculo,
 * y en JSON sus pasos, su historia y los mensajes que mandaron y recibieron. Cada tabla se recorre de a pedazos: una
 * tienda con muchos casos no entra entera en memoria. El CSV lo escribe el paquete, que neutraliza lo que una hoja de
 * calculo leeria como formula.
 */
@Component
@Order(3)
public class ExportacionDeCasos implements ParteDeLaExportacion {

    static final String CASOS = """
            select c.id, c.proceso_id, v.numero as version_del_proceso, c.referencia, c.estado, c.tick_inicio,
                   c.tick_fin, c.fecha_creacion, c.fecha_fin, c.variables
            from casos c join versiones_proceso v on v.id = c.version_proceso_id
            where c.empresa_id = ? and c.id > ? order by c.id limit ?
            """;

    /** Las columnas del CSV de los casos, en el orden de la consulta: las mismas claves que el JSON. */
    static final List<String> COLUMNAS_DE_CASOS = List.of("id", "procesoId", "versionDelProceso", "referencia",
            "estado", "tickInicio", "tickFin", "fechaCreacion", "fechaFin", "variables");

    static final String ACTIVIDADES = """
            select id, caso_id, nodo_id, nodo_nombre, tipo_nodo, subtipo, rol_proceso_id, estado, llegadas, asignado_a,
                   tick_inicio, tick_fin, datos_salida, fecha_creacion, fecha_modificacion
            from actividades_caso where empresa_id = ? and id > ? order by id limit ?
            """;

    static final String EVENTOS = """
            select id, caso_id, tick, fecha, tipo, detalle, autor_id
            from eventos_caso where empresa_id = ? and id > ? order by id limit ?
            """;

    static final String MENSAJES_ENTRANTES = """
            select id, proceso_id, caso_id, nombre, clave, origen, clave_externa, resultado, cuerpo, tick,
                   tick_disponible, fecha
            from mensajes_entrantes where empresa_id = ? and id > ? order by id limit ?
            """;

    static final String MENSAJES_SALIENTES = """
            select id, caso_id, mensaje_id, nombre, pool_destino_nombre, integracion, tipo_destino, clave, estado,
                   cuerpo, tick_creacion, tick_entrega, intentos, error, fecha
            from mensajes_salientes where empresa_id = ? and id > ? order by id limit ?
            """;

    private final LecturaDeFilas lector;

    public ExportacionDeCasos(LecturaDeFilas lector) {
        this.lector = lector;
    }

    @Override
    public void escribir(Long empresaId, Paquete paquete) {
        try (FilasJson casos = paquete.filasJson("casos/casos.json")) {
            lector.recorrer(CASOS, empresaId, casos::agregar);
        }
        try (FilasCsv casos = paquete.filasCsv("casos/casos.csv", COLUMNAS_DE_CASOS)) {
            lector.recorrer(CASOS, empresaId,
                    caso -> casos.agregar(COLUMNAS_DE_CASOS.stream().map(caso::get).toList()));
        }
        tabla(paquete, "casos/actividades.json", ACTIVIDADES, empresaId);
        tabla(paquete, "casos/eventos.json", EVENTOS, empresaId);
        tabla(paquete, "casos/mensajes-entrantes.json", MENSAJES_ENTRANTES, empresaId);
        tabla(paquete, "casos/mensajes-salientes.json", MENSAJES_SALIENTES, empresaId);
    }

    private void tabla(Paquete paquete, String ruta, String consulta, Long empresaId) {
        try (FilasJson filas = paquete.filasJson(ruta)) {
            lector.recorrer(consulta, empresaId, filas::agregar);
        }
    }
}
