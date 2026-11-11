package com.facimus.procesos.modelado.service.impl;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
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

import com.facimus.procesos.common.security.ContextoDeTienda;
import com.facimus.procesos.modelado.dto.response.PresenciaResponse;
import com.facimus.procesos.modelado.model.Presencia;
import com.facimus.procesos.modelado.repository.PresenciaRepository;

import jakarta.annotation.PreDestroy;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * PR 58: lo que pasa en un proceso, para quien lo este mirando en cualquier replica. Se publica con {@code NOTIFY}, que
 * sale al confirmarse la transaccion, y cada replica se lo manda a sus conexiones. Un aviso va chico: la lista de
 * quien esta se lee de la base al recibirlo.
 */
@Component
public class CanalEnVivo {

    /** El canal de PostgreSQL. */
    static final String CANAL = "en_vivo";
    static final String PRESENCIA = "presencia";

    /** Sin latido durante esto, una conexion se da por muerta, aunque su replica se haya caido sin despedirse. */
    private static final Duration VENCIMIENTO = Duration.ofSeconds(60);

    private static final Logger log = LoggerFactory.getLogger(CanalEnVivo.class);

    /** Una conexion abierta en esta replica. */
    private record Conexion(String id, Long empresaId, Long procesoId, SseEmitter emisor) {
    }

    private final Map<String, Conexion> conexiones = new ConcurrentHashMap<>();
    private final ExecutorService envios = Executors.newVirtualThreadPerTaskExecutor();
    private final JdbcTemplate jdbc;
    private final JsonMapper json;
    private final PresenciaRepository presenciaRepository;

    public CanalEnVivo(JdbcTemplate jdbc, JsonMapper json, PresenciaRepository presenciaRepository) {
        this.jdbc = jdbc;
        this.json = json;
        this.presenciaRepository = presenciaRepository;
    }

    /** Sale al confirmarse la transaccion en curso: si se deshace, nadie se entera. */
    void publicar(Long empresaId, Long procesoId, String tipo, Map<String, Object> datos) {
        Map<String, Object> aviso = new HashMap<>();
        aviso.put("empresaId", empresaId);
        aviso.put("procesoId", procesoId);
        aviso.put("tipo", tipo);
        aviso.put("datos", datos);
        jdbc.query("select pg_notify(?, ?)", resultado -> null, CANAL, json.writeValueAsString(aviso));
    }

    void registrar(String conexion, Long empresaId, Long procesoId, SseEmitter emisor) {
        conexiones.put(conexion, new Conexion(conexion, empresaId, procesoId, emisor));
    }

    /** Devuelve si la conexion seguia registrada. */
    boolean quitar(String conexion) {
        return conexiones.remove(conexion) != null;
    }

    /** Un aviso que llego por {@code LISTEN}: va a las conexiones de ese proceso que haya en esta replica. */
    void recibir(String aviso) {
        JsonNode nodo = json.readTree(aviso);
        Long empresaId = nodo.get("empresaId").longValue();
        Long procesoId = nodo.get("procesoId").longValue();
        String tipo = nodo.get("tipo").asString();
        List<Conexion> destinos = conexiones.values().stream()
                .filter(una -> una.procesoId().equals(procesoId) && una.empresaId().equals(empresaId))
                .toList();
        if (destinos.isEmpty()) {
            return;
        }
        Object datos = PRESENCIA.equals(tipo) ? Map.of("personas", personas(empresaId, procesoId)) : nodo.get("datos");
        destinos.forEach(destino -> envios.execute(() -> enviar(destino, tipo, datos)));
    }

    /** Quien esta en un proceso: cada persona una vez, aunque tenga varias pestanas. */
    List<PresenciaResponse> personas(Long empresaId, Long procesoId) {
        Map<Long, PresenciaResponse> personas = new java.util.LinkedHashMap<>();
        ContextoDeTienda.comoSistema(() -> presenciaRepository
                .findAllByProcesoIdAndEmpresaIdOrderByIdAsc(procesoId, empresaId)
                .forEach(una -> personas.putIfAbsent(una.getUsuarioId(),
                        new PresenciaResponse(una.getUsuarioId(), una.getNombre(), una.getDesde()))));
        return List.copyOf(personas.values());
    }

    private void enviar(Conexion conexion, String tipo, Object datos) {
        try {
            conexion.emisor().send(SseEmitter.event().name(tipo).data(datos));
        } catch (IOException | IllegalStateException cortada) {
            log.debug("Una conexion en vivo se corto al enviarle {}", tipo, cortada);
            conexion.emisor().completeWithError(cortada);
        }
    }

    /**
     * El latido, cada 25 segundos: un comentario que mantiene la conexion y deja ver si sigue viva, la marca en la base,
     * y barre lo que nadie marco, como las conexiones de una replica que se cayo.
     */
    void latir() {
        List<String> vivas = new ArrayList<>();
        for (Conexion conexion : conexiones.values()) {
            try {
                conexion.emisor().send(SseEmitter.event().comment("latido"));
                vivas.add(conexion.id());
            } catch (IOException | IllegalStateException cortada) {
                log.debug("Una conexion en vivo no respondio al latido", cortada);
                conexion.emisor().completeWithError(cortada);
            }
        }
        ContextoDeTienda.comoSistema(() -> {
            if (!vivas.isEmpty()) {
                presenciaRepository.latir(vivas, LocalDateTime.now());
            }
            barrer();
        });
    }

    /** Se ejecuta como sistema: una replica barre lo de todas las tiendas. */
    private void barrer() {
        List<Presencia> muertas = presenciaRepository.findAllByUltimoLatidoBefore(
                LocalDateTime.now().minus(VENCIMIENTO));
        if (muertas.isEmpty()) {
            return;
        }
        presenciaRepository.borrar(muertas.stream().map(Presencia::getId).toList());
        muertas.stream().map(una -> List.of(una.getEmpresa().getId(), una.getProcesoId())).distinct()
                .forEach(par -> publicar(par.get(0), par.get(1), PRESENCIA, Map.of()));
    }

    @PreDestroy
    void cerrar() {
        envios.shutdown();
    }
}
