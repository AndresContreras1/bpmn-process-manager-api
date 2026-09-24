package com.facimus.procesos.modelado.service.impl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import com.facimus.procesos.modelado.dto.response.ArcoResponse;
import com.facimus.procesos.modelado.dto.response.DiagramaResponse;
import com.facimus.procesos.modelado.dto.response.LaneResponse;
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
}
