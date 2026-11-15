package com.facimus.procesos.gestion.service.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.facimus.procesos.common.almacen.Almacen;
import com.facimus.procesos.common.almacen.ArchivoNoEncontradoException;
import com.facimus.procesos.gestion.service.BitacoraService;
import com.facimus.procesos.gestion.service.LecturaDeFilas;
import com.facimus.procesos.gestion.service.Paquete;
import com.facimus.procesos.gestion.service.Paquete.FilasJson;
import com.facimus.procesos.gestion.service.ParteDeLaExportacion;

/**
 * PR 66: lo que gestion pone en la exportacion: la tienda y su configuracion, las personas sin ningun secreto, los
 * roles, las carpetas y las concesiones, cada proceso con sus versiones (el JSON que se publico, su BPMN y su dibujo),
 * el historial, y la bitacora con sus hashes y sus anclas para verificarla fuera. Cada consulta nombra sus columnas:
 * lo que no esta nombrado no sale. De las personas no salen el hash de la clave ni el identificador de sus passkeys,
 * y las tablas de los secretos (segundos factores, codigos de recuperacion, passkeys, sesiones, tokens y enlaces del
 * correo) no se leen.
 */
@Component
@Order(1)
public class ExportacionDeGestion implements ParteDeLaExportacion {

    static final String TIENDA = """
            select id, nombre, nit, correo_contacto, fecha_registro, baja_solicitada_en from empresas where id = ?
            """;

    static final String CONFIGURACION = """
            select politica_estructura, politica_segundo_factor, solo_entrada_externa, inactividad_sesion_minutos,
                   duracion_sesion_horas, idioma, zona_horaria, modo_simulacion, reloj, semilla, tasa_rechazo_pagos,
                   ticks_respuesta_pagos, regla_rechazo_pagos, ticks_respuesta_transporte, ticks_entrega,
                   tasa_perdida_envios, tasa_fallo_notificaciones
            from configuracion_tienda where empresa_id = ?
            """;

    static final String PERSONAS = """
            select id, nombre, email, rol_acceso, activo, debe_cambiar_clave, correo_verificado_en, anonimizado_en,
                   idioma, zona_horaria, fecha_creacion, fecha_modificacion
            from usuarios where empresa_id = ? and id > ? order by id limit ?
            """;

    static final String ROLES = """
            select id, nombre, descripcion, activo, fecha_creacion, fecha_modificacion
            from roles_proceso where empresa_id = ? and id > ? order by id limit ?
            """;

    static final String MEMBRESIAS = """
            select id, usuario_id, rol_proceso_id
            from membresias_rol where empresa_id = ? and id > ? order by id limit ?
            """;

    static final String CARPETAS = """
            select id, padre_id, nombre, fecha_creacion, fecha_modificacion
            from carpetas where empresa_id = ? and id > ? order by id limit ?
            """;

    static final String CONCESIONES = """
            select id, carpeta_id, proceso_id, sujeto, usuario_id, rol_proceso_id, accion
            from concesiones where empresa_id = ? and id > ? order by id limit ?
            """;

    static final String PROCESOS = """
            select id, nombre, descripcion, categoria, modo, estado, activo, version_publicada, huella_publicada,
                   carpeta_id, dueno_id, proxima_revision, clasificacion, etiquetas, creado_por, fecha_creacion,
                   modificado_por, fecha_modificacion
            from procesos where empresa_id = ? and id > ? order by id limit ?
            """;

    static final String VERSIONES = """
            select numero, estado, fecha_publicacion, publicado_por, huella
            from versiones_proceso where empresa_id = ? and proceso_id = ? order by numero
            """;

    static final String CONTENIDO_DE_LA_VERSION = """
            select definicion, modelo_bpmn, svg from versiones_proceso
            where empresa_id = ? and proceso_id = ? and numero = ?
            """;

    static final String HISTORIAL = """
            select id, proceso_id, recurso_tipo, recurso_id, autor_id, fecha_cambio, descripcion_cambio
            from historial_cambios where empresa_id = ? and id > ? order by id limit ?
            """;

    /** Cada campo que entra en el hash de un registro, en el orden en que entra, y el hash. */
    static final String BITACORA = """
            select empresa_id, secuencia, momento, actor_tipo, actor_id, accion, recurso, recurso_id, exito, ip,
                   user_agent, request_id, trace_id, detalle, hash_anterior, hash
            from bitacora where empresa_id = ? and secuencia > ? order by secuencia limit ?
            """;

    static final String ANCLAS = """
            select dia, secuencia, hash, clave, inmutable, fecha from anclas_bitacora where empresa_id = ? order by dia
            """;

    private static final String ID = "id";
    private static final String NUMERO = "numero";
    private static final String CLAVE = "clave";

    private final LecturaDeFilas lector;
    private final BitacoraService bitacoraService;
    private final Almacen almacen;

    public ExportacionDeGestion(LecturaDeFilas lector, BitacoraService bitacoraService, Almacen almacen) {
        this.lector = lector;
        this.bitacoraService = bitacoraService;
        this.almacen = almacen;
    }

    @Override
    public void escribir(Long empresaId, Paquete paquete) {
        paquete.json("organizacion.json", organizacion(empresaId));
        tabla(paquete, "personas.json", PERSONAS, empresaId);
        tabla(paquete, "roles.json", ROLES, empresaId);
        tabla(paquete, "membresias.json", MEMBRESIAS, empresaId);
        tabla(paquete, "carpetas.json", CARPETAS, empresaId);
        tabla(paquete, "concesiones.json", CONCESIONES, empresaId);
        escribirProcesos(empresaId, paquete);
        tabla(paquete, "historial.json", HISTORIAL, empresaId);
        escribirBitacora(empresaId, paquete);
    }

    private Map<String, Object> organizacion(Long empresaId) {
        Map<String, Object> organizacion = new LinkedHashMap<>();
        organizacion.put("tienda", unaFila(TIENDA, empresaId));
        organizacion.put("configuracion", unaFila(CONFIGURACION, empresaId));
        return organizacion;
    }

    /** La unica fila de la consulta, o nada si no la hay. */
    private Map<String, Object> unaFila(String consulta, Long empresaId) {
        List<Map<String, Object>> filas = lector.filas(consulta, empresaId);
        return filas.isEmpty() ? Map.of() : filas.getFirst();
    }

    private void tabla(Paquete paquete, String ruta, String consulta, Long empresaId) {
        try (FilasJson filas = paquete.filasJson(ruta)) {
            lector.recorrer(consulta, empresaId, filas::agregar);
        }
    }

    /**
     * El indice de los procesos y, en la carpeta de cada uno, su ficha y sus versiones. El indice se lee entero antes
     * de escribirlo: un archivo del ZIP se escribe completo antes del siguiente, y una tienda tiene cientos de
     * procesos, no millones.
     */
    private void escribirProcesos(Long empresaId, Paquete paquete) {
        List<Map<String, Object>> procesos = new ArrayList<>();
        lector.recorrer(PROCESOS, empresaId, procesos::add);
        paquete.json("procesos/procesos.json", procesos);
        for (Map<String, Object> proceso : procesos) {
            long procesoId = ((Number) proceso.get(ID)).longValue();
            String carpeta = Paquete.carpetaDeProceso(procesoId, (String) proceso.get("nombre"));
            paquete.json(carpeta + "/proceso.json", proceso);
            escribirVersiones(empresaId, procesoId, carpeta + "/versiones/", paquete);
        }
    }

    /** Cada version como se publico: su diagrama en JSON, su BPMN y su dibujo, si lo tiene. */
    private void escribirVersiones(Long empresaId, long procesoId, String carpeta, Paquete paquete) {
        List<Map<String, Object>> versiones = lector.filas(VERSIONES, empresaId, procesoId);
        if (versiones.isEmpty()) {
            return;
        }
        paquete.json(carpeta + "versiones.json", versiones);
        for (Map<String, Object> version : versiones) {
            Object numero = version.get(NUMERO);
            String nombre = carpeta + "v" + numero;
            // De a una version: el diagrama, el BPMN y el dibujo de todas juntas pueden pesar mucho
            for (Map<String, Object> contenido : lector.filas(CONTENIDO_DE_LA_VERSION, empresaId, procesoId,
                    numero)) {
                escribirSiHay(paquete, nombre + ".json", contenido.get("definicion"));
                escribirSiHay(paquete, nombre + ".bpmn", contenido.get("modeloBpmn"));
                escribirSiHay(paquete, nombre + ".svg", contenido.get("svg"));
            }
        }
    }

    private static Map<String, Object> sinClave(Map<String, Object> ancla) {
        Map<String, Object> exportada = new LinkedHashMap<>(ancla);
        exportada.remove(CLAVE);
        return exportada;
    }

    private static void escribirSiHay(Paquete paquete, String ruta, Object contenido) {
        if (contenido != null) {
            paquete.texto(ruta, contenido.toString());
        }
    }

    /**
     * La bitacora entera en el orden de su cadena, con cada campo del hash; su verificacion de hoy; y cada ancla, con
     * el archivo que guarda el almacen inmutable. Con eso la cadena se recalcula fuera, sin la API.
     */
    private void escribirBitacora(Long empresaId, Paquete paquete) {
        try (FilasJson filas = paquete.filasJson("bitacora/bitacora.json")) {
            lector.recorrer(BITACORA, empresaId, "secuencia", filas::agregar);
        }
        paquete.json("bitacora/verificacion.json", bitacoraService.verificar(empresaId));
        List<Map<String, Object>> anclas = lector.filas(ANCLAS, empresaId);
        // La clave del almacen sirve para abrir el archivo, y no sale: ninguna clave del almacen se exporta
        paquete.json("bitacora/anclas.json", anclas.stream().map(ExportacionDeGestion::sinClave).toList());
        for (Map<String, Object> ancla : anclas) {
            try (InputStream archivo = almacen.abrirInmutable((String) ancla.get(CLAVE))) {
                paquete.copiar("bitacora/anclas/" + ancla.get("dia") + ".json", archivo);
            } catch (ArchivoNoEncontradoException _) {
                // La fila dice que el almacen ya no la tiene, y la verificacion de arriba lo cuenta
            } catch (IOException e) {
                throw new UncheckedIOException("No se pudo leer el ancla del " + ancla.get("dia"), e);
            }
        }
    }
}
