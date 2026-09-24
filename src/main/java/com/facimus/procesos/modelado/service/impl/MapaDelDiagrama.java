package com.facimus.procesos.modelado.service.impl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import com.facimus.procesos.modelado.dto.response.ArcoResponse;
import com.facimus.procesos.modelado.dto.response.CorrelacionResponse;
import com.facimus.procesos.modelado.dto.response.DiagramaResponse;
import com.facimus.procesos.modelado.dto.response.LaneResponse;
import com.facimus.procesos.modelado.dto.response.MensajeResponse;
import com.facimus.procesos.modelado.dto.response.PoolResponse;
import com.facimus.procesos.modelado.model.TipoParticipante;

/**
 * El diagrama indexado por id. El diagnostico recorre sus listas una sola vez al construirlo y despues pregunta por
 * clave, asi que revisar un diagrama cuesta lo mismo aunque cada regla mire los mismos elementos.
 *
 * <p>Las listas conservan el orden en que llegan del diagrama, que es el orden por id: con la misma entrada, los
 * hallazgos salen siempre en el mismo orden.
 */
final class MapaDelDiagrama {

    private final DiagramaResponse diagrama;
    private final Map<Long, PoolResponse> pools = new LinkedHashMap<>();
    private final Map<Long, List<LaneResponse>> lanesPorPool = new LinkedHashMap<>();
    private final Map<Long, Long> poolDeLane = new LinkedHashMap<>();
    private final Map<Long, NodoDelDiagrama> nodos = new LinkedHashMap<>();
    private final Map<Long, List<NodoDelDiagrama>> nodosPorLane = new LinkedHashMap<>();
    private final Map<Long, List<ArcoResponse>> salidas = new LinkedHashMap<>();
    private final Map<Long, List<ArcoResponse>> entradas = new LinkedHashMap<>();
    private final Map<Long, List<MensajeResponse>> mensajesQueSalenDe = new LinkedHashMap<>();
    private final Map<Long, List<MensajeResponse>> mensajesQueLleganA = new LinkedHashMap<>();
    private final Map<Long, List<MensajeResponse>> mensajesDePool = new LinkedHashMap<>();
    private final Map<Long, CorrelacionResponse> correlaciones = new LinkedHashMap<>();

    MapaDelDiagrama(DiagramaResponse diagrama) {
        this.diagrama = diagrama;
        diagrama.pools().forEach(pool -> pools.put(pool.id(), pool));
        diagrama.lanes().forEach(lane -> {
            poolDeLane.put(lane.id(), lane.poolId());
            lanesPorPool.computeIfAbsent(lane.poolId(), sinLanes -> new ArrayList<>()).add(lane);
        });
        Stream.concat(Stream.concat(
                        diagrama.actividades().stream().map(NodoDelDiagrama::de),
                        diagrama.gateways().stream().map(NodoDelDiagrama::de)),
                        diagrama.eventos().stream().map(NodoDelDiagrama::de))
                .forEach(nodo -> {
                    nodos.put(nodo.id(), nodo);
                    nodosPorLane.computeIfAbsent(nodo.laneId(), sinNodos -> new ArrayList<>()).add(nodo);
                });
        diagrama.arcos().forEach(arco -> {
            salidas.computeIfAbsent(arco.origenId(), sinArcos -> new ArrayList<>()).add(arco);
            entradas.computeIfAbsent(arco.destinoId(), sinArcos -> new ArrayList<>()).add(arco);
        });
        diagrama.mensajes().forEach(mensaje -> {
            anotar(mensajesQueSalenDe, mensaje.nodoOrigenId(), mensaje);
            anotar(mensajesQueLleganA, mensaje.nodoDestinoId(), mensaje);
            anotar(mensajesDePool, mensaje.poolOrigenId(), mensaje);
            anotar(mensajesDePool, mensaje.poolDestinoId(), mensaje);
        });
        diagrama.correlaciones().forEach(correlacion -> correlaciones.put(correlacion.mensajeId(), correlacion));
    }

    private static void anotar(Map<Long, List<MensajeResponse>> indice, Long clave, MensajeResponse mensaje) {
        if (clave != null) {
            indice.computeIfAbsent(clave, sinMensajes -> new ArrayList<>()).add(mensaje);
        }
    }

    DiagramaResponse diagrama() {
        return diagrama;
    }

    List<PoolResponse> pools() {
        return diagrama.pools();
    }

    /**
     * El pool de la tienda, el unico que se ejecuta: los demas son participantes con los que se intercambian
     * mensajes. Un proceso siempre lo tiene, porque se crea junto al proceso, pero el diagnostico no lo da por hecho.
     */
    Optional<PoolResponse> poolDeLaEmpresa() {
        return diagrama.pools().stream()
                .filter(pool -> pool.tipoParticipante() == TipoParticipante.EMPRESA)
                .findFirst();
    }

    PoolResponse pool(Long poolId) {
        return pools.get(poolId);
    }

    /** El pool al que pertenece un nodo, a traves de su lane. */
    Long poolDelNodo(Long nodoId) {
        NodoDelDiagrama nodo = nodos.get(nodoId);
        return nodo == null ? null : poolDeLane.get(nodo.laneId());
    }

    List<LaneResponse> lanesDe(Long poolId) {
        return lanesPorPool.getOrDefault(poolId, List.of());
    }

    /** Un pool se modela por dentro cuando tiene lanes; uno de caja negra solo se ve desde fuera. */
    boolean seModelaPorDentro(Long poolId) {
        return !lanesDe(poolId).isEmpty();
    }

    NodoDelDiagrama nodo(Long nodoId) {
        return nodos.get(nodoId);
    }

    List<NodoDelDiagrama> nodos() {
        return List.copyOf(nodos.values());
    }

    List<NodoDelDiagrama> nodosDe(Long poolId) {
        return lanesDe(poolId).stream()
                .flatMap(lane -> nodosPorLane.getOrDefault(lane.id(), List.<NodoDelDiagrama>of()).stream())
                .toList();
    }

    List<NodoDelDiagrama> nodosDeLane(Long laneId) {
        return nodosPorLane.getOrDefault(laneId, List.of());
    }

    List<ArcoResponse> salidasDe(Long nodoId) {
        return salidas.getOrDefault(nodoId, List.of());
    }

    List<ArcoResponse> entradasDe(Long nodoId) {
        return entradas.getOrDefault(nodoId, List.of());
    }

    /** Los mensajes que arrancan en este nodo, es decir los que el nodo manda. */
    List<MensajeResponse> mensajesQueSalenDe(Long nodoId) {
        return mensajesQueSalenDe.getOrDefault(nodoId, List.of());
    }

    /** Los mensajes anclados a este nodo como destino, es decir los que el nodo espera. */
    List<MensajeResponse> mensajesQueLleganA(Long nodoId) {
        return mensajesQueLleganA.getOrDefault(nodoId, List.of());
    }

    /** Todo lo que el pool intercambia, entre y salga. */
    List<MensajeResponse> mensajesDe(Long poolId) {
        return mensajesDePool.getOrDefault(poolId, List.of());
    }

    /** La clave con la que un mensaje encuentra su caso, si se definio. */
    CorrelacionResponse correlacionDe(Long mensajeId) {
        return correlaciones.get(mensajeId);
    }
}
