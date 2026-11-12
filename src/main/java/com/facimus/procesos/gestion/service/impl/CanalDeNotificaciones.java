package com.facimus.procesos.gestion.service.impl;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.facimus.procesos.common.avisos.ReceptorDeAvisos;

import jakarta.annotation.PreDestroy;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * PR 63: las notificaciones en vivo, para la persona que las recibe en cualquier replica. Como el en vivo de un
 * proceso (PR 58): se publican con {@code NOTIFY}, que sale al confirmarse la transaccion, y cada replica las manda a
 * las conexiones que tenga abiertas esa persona. Las conexiones viven solo en memoria: abrir un flujo no escribe nada.
 */
@Component
public class CanalDeNotificaciones implements ReceptorDeAvisos {

    /** El canal de PostgreSQL. */
    static final String CANAL = "notificaciones";
    static final String NOTIFICACION = "notificacion";
    static final String CONTEO = "conteo";

    private static final Logger log = LoggerFactory.getLogger(CanalDeNotificaciones.class);

    /** Una conexion abierta en esta replica. */
    private record Conexion(Long empresaId, Long usuarioId, SseEmitter emisor) {
    }

    private final Map<String, Conexion> conexiones = new ConcurrentHashMap<>();
    private final ExecutorService envios = Executors.newVirtualThreadPerTaskExecutor();
    private final JdbcTemplate jdbc;
    private final JsonMapper json;

    public CanalDeNotificaciones(JdbcTemplate jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public String canal() {
        return CANAL;
    }

    /** Sale al confirmarse la transaccion en curso: si se deshace, nadie se entera. */
    void publicar(Long empresaId, Long usuarioId, String tipo, Object datos) {
        Map<String, Object> aviso = new HashMap<>();
        aviso.put("empresaId", empresaId);
        aviso.put("usuarioId", usuarioId);
        aviso.put("tipo", tipo);
        aviso.put("datos", datos);
        jdbc.query("select pg_notify(?, ?)", resultado -> null, CANAL, json.writeValueAsString(aviso));
    }

    void registrar(String conexion, Long empresaId, Long usuarioId, SseEmitter emisor) {
        conexiones.put(conexion, new Conexion(empresaId, usuarioId, emisor));
    }

    void quitar(String conexion) {
        conexiones.remove(conexion);
    }

    /** Las conexiones de una persona en esta replica. */
    long abiertas(Long empresaId, Long usuarioId) {
        return conexiones.values().stream().filter(una -> una.empresaId().equals(empresaId)
                && una.usuarioId().equals(usuarioId)).count();
    }

    /** Un aviso que llego por {@code LISTEN}: va a las conexiones de esa persona que haya en esta replica. */
    @Override
    public void recibir(String aviso) {
        JsonNode nodo = json.readTree(aviso);
        Long empresaId = nodo.get("empresaId").longValue();
        Long usuarioId = nodo.get("usuarioId").longValue();
        String tipo = nodo.get("tipo").asString();
        JsonNode datos = nodo.get("datos");
        List<Conexion> destinos = conexiones.values().stream()
                .filter(una -> una.usuarioId().equals(usuarioId) && una.empresaId().equals(empresaId)).toList();
        destinos.forEach(destino -> envios.execute(() -> enviar(destino.emisor(), tipo, datos)));
    }

    /** Un comentario cada 25 segundos: mantiene la conexion abierta y deja ver si sigue viva. */
    @Override
    public void latir() {
        conexiones.values().forEach(conexion -> {
            try {
                conexion.emisor().send(SseEmitter.event().comment("latido"));
            } catch (IOException | IllegalStateException cortada) {
                log.debug("Un flujo de notificaciones no respondio al latido", cortada);
                conexion.emisor().completeWithError(cortada);
            }
        });
    }

    private static void enviar(SseEmitter emisor, String tipo, Object datos) {
        try {
            emisor.send(SseEmitter.event().name(tipo).data(datos));
        } catch (IOException | IllegalStateException cortada) {
            log.debug("Un flujo de notificaciones se corto al enviarle {}", tipo, cortada);
            emisor.completeWithError(cortada);
        }
    }

    @PreDestroy
    void cerrar() {
        envios.shutdown();
    }
}
