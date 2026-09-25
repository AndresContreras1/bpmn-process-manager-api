package com.facimus.procesos.ejecucion.service.impl;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.facimus.procesos.common.condiciones.Condicion;
import com.facimus.procesos.common.condiciones.CondicionMalEscrita;
import com.facimus.procesos.common.condiciones.EvaluadorDeCondiciones;
import com.facimus.procesos.ejecucion.model.TipoNodoCaso;
import com.facimus.procesos.modelado.dto.response.ActividadResponse;
import com.facimus.procesos.modelado.dto.response.ArcoResponse;
import com.facimus.procesos.modelado.dto.response.DiagramaResponse;
import com.facimus.procesos.modelado.dto.response.EventoResponse;
import com.facimus.procesos.modelado.dto.response.GatewayResponse;
import com.facimus.procesos.modelado.dto.response.LaneResponse;
import com.facimus.procesos.modelado.dto.response.PoolResponse;
import com.facimus.procesos.modelado.model.TipoEvento;
import com.facimus.procesos.modelado.model.TipoParticipante;

/**
 * El diagrama de una version publicada, ya masticado para ejecutarlo: los nodos por id, las salidas y las entradas
 * de cada uno, las condiciones compiladas y, para el join inclusivo, que nodos se pueden alcanzar desde cual.
 *
 * <p>Solo entran los nodos del pool de la tienda. Los demas participantes son socios o cajas negras: con ellos se
 * intercambian mensajes, pero su interior, si lo tienen dibujado, es documentacion y no se ejecuta.
 *
 * <p>Se construye a partir de la instantanea de la version, nunca del modelo vivo, y es inmutable: la misma version
 * siempre da el mismo grafo. Hoy se arma cada vez que hace falta; como no cambia nunca, es el candidato de la cache
 * de segundo nivel, que espera a que el PR de carga diga cuanto cuesta armarlo (D19).
 */
final class GrafoDeVersion {

    private final Long poolDeLaTienda;
    private final Map<Long, NodoDeLaVersion> nodos;
    private final Map<Long, List<ArcoDeLaVersion>> salidas;
    private final Map<Long, List<ArcoDeLaVersion>> entradas;
    private final Map<Long, String> mensajesQueEsperan;
    private final Map<Long, Set<Long>> alcanzablesDesde;

    private GrafoDeVersion(Long poolDeLaTienda, Map<Long, NodoDeLaVersion> nodos,
            Map<Long, List<ArcoDeLaVersion>> salidas, Map<Long, List<ArcoDeLaVersion>> entradas,
            Map<Long, String> mensajesQueEsperan) {
        this.poolDeLaTienda = poolDeLaTienda;
        this.nodos = nodos;
        this.salidas = salidas;
        this.entradas = entradas;
        this.mensajesQueEsperan = mensajesQueEsperan;
        this.alcanzablesDesde = alcanzables(nodos.keySet(), salidas);
    }

    /**
     * Arma el grafo del pool de la tienda. Un diagrama sin pool de empresa da un grafo vacio: no se puede abrir un
     * caso sobre el, y quien lo intente recibe la regla, no una excepcion a medio camino.
     */
    static GrafoDeVersion de(DiagramaResponse diagrama) {
        Long pool = diagrama.pools().stream()
                .filter(participante -> participante.tipoParticipante() == TipoParticipante.EMPRESA)
                .map(PoolResponse::id)
                .findFirst()
                .orElse(null);
        Map<Long, Long> rolDeLane = new HashMap<>();
        Set<Long> lanesDelPool = new HashSet<>();
        for (LaneResponse lane : diagrama.lanes()) {
            if (lane.poolId().equals(pool)) {
                lanesDelPool.add(lane.id());
                rolDeLane.put(lane.id(), lane.rolProcesoId());
            }
        }
        Map<Long, NodoDeLaVersion> nodos = nodosDelPool(diagrama, lanesDelPool, rolDeLane);
        Map<Long, List<ArcoDeLaVersion>> salidas = new LinkedHashMap<>();
        Map<Long, List<ArcoDeLaVersion>> entradas = new LinkedHashMap<>();
        for (ArcoResponse arco : diagrama.arcos()) {
            if (!nodos.containsKey(arco.origenId()) || !nodos.containsKey(arco.destinoId())) {
                continue;
            }
            ArcoDeLaVersion salida = new ArcoDeLaVersion(arco.id(), arco.origenId(), arco.destinoId(),
                    arco.etiqueta(), arco.porDefecto(), arco.orden(), compilar(arco.condicion()));
            salidas.computeIfAbsent(arco.origenId(), sinArcos -> new ArrayList<>()).add(salida);
            entradas.computeIfAbsent(arco.destinoId(), sinArcos -> new ArrayList<>()).add(salida);
        }
        salidas.values().forEach(lista -> lista.sort(POR_ORDEN));
        Map<Long, String> mensajesQueEsperan = new LinkedHashMap<>();
        diagrama.mensajes().stream()
                .filter(mensaje -> mensaje.nodoDestinoId() != null && nodos.containsKey(mensaje.nodoDestinoId()))
                .forEach(mensaje -> mensajesQueEsperan.putIfAbsent(mensaje.nodoDestinoId(), mensaje.nombre()));
        return new GrafoDeVersion(pool, nodos, salidas, entradas, mensajesQueEsperan);
    }

    /** Un gateway evalua sus salidas por el orden que se les dio, y el id desempata: siempre deciden igual. */
    private static final Comparator<ArcoDeLaVersion> POR_ORDEN =
            Comparator.comparingInt(ArcoDeLaVersion::orden).thenComparing(ArcoDeLaVersion::id);

    private static Map<Long, NodoDeLaVersion> nodosDelPool(DiagramaResponse diagrama, Set<Long> lanes,
            Map<Long, Long> rolDeLane) {
        Map<Long, NodoDeLaVersion> nodos = new LinkedHashMap<>();
        for (ActividadResponse actividad : diagrama.actividades()) {
            if (lanes.contains(actividad.laneId())) {
                nodos.put(actividad.id(), new NodoDeLaVersion(actividad.id(), actividad.nombre(),
                        TipoNodoCaso.ACTIVIDAD, actividad.tipoActividad().name(), actividad.laneId(),
                        rolDeLane.get(actividad.laneId())));
            }
        }
        for (GatewayResponse gateway : diagrama.gateways()) {
            if (lanes.contains(gateway.laneId())) {
                nodos.put(gateway.id(), new NodoDeLaVersion(gateway.id(), gateway.nombre(), TipoNodoCaso.GATEWAY,
                        gateway.tipoGateway().name(), gateway.laneId(), rolDeLane.get(gateway.laneId())));
            }
        }
        for (EventoResponse evento : diagrama.eventos()) {
            if (lanes.contains(evento.laneId())) {
                nodos.put(evento.id(), new NodoDeLaVersion(evento.id(), evento.nombre(), TipoNodoCaso.EVENTO,
                        evento.tipoEvento().name(), evento.laneId(), rolDeLane.get(evento.laneId())));
            }
        }
        return nodos;
    }

    /**
     * Una condicion de una version publicada ya paso el diagnostico, asi que compila. Si aun asi no compilara, el
     * arco se queda sin condicion en vez de tumbar el caso: un gateway sin ninguna salida verdadera se va por su
     * salida por defecto, y si no la tiene queda registrado como sin camino, que es lo que hay que mirar.
     */
    private static Condicion compilar(String condicion) {
        if (condicion == null || condicion.isBlank()) {
            return null;
        }
        try {
            return EvaluadorDeCondiciones.compilar(condicion);
        } catch (CondicionMalEscrita noCompila) {
            return null;
        }
    }

    /**
     * Que nodos se pueden alcanzar desde cada uno siguiendo los arcos. Lo necesita el join inclusivo, que espera
     * mientras algun otro token vivo todavia pueda llegar hasta el. Se calcula una vez al armar el grafo.
     */
    private static Map<Long, Set<Long>> alcanzables(Set<Long> nodos, Map<Long, List<ArcoDeLaVersion>> salidas) {
        Map<Long, Set<Long>> alcance = new HashMap<>();
        for (Long nodo : nodos) {
            Set<Long> vistos = new HashSet<>();
            Deque<Long> porVisitar = new ArrayDeque<>();
            porVisitar.push(nodo);
            while (!porVisitar.isEmpty()) {
                Long actual = porVisitar.pop();
                for (ArcoDeLaVersion arco : salidas.getOrDefault(actual, List.of())) {
                    if (vistos.add(arco.destinoId())) {
                        porVisitar.push(arco.destinoId());
                    }
                }
            }
            alcance.put(nodo, vistos);
        }
        return alcance;
    }

    /** El pool de la tienda, el unico que se ejecuta. Vacio en un diagrama que no lo tiene. */
    Optional<Long> poolDeLaTienda() {
        return Optional.ofNullable(poolDeLaTienda);
    }

    boolean estaVacio() {
        return nodos.isEmpty();
    }

    Optional<NodoDeLaVersion> nodo(Long nodoId) {
        return Optional.ofNullable(nodos.get(nodoId));
    }

    /**
     * El evento por el que se abre un caso a mano. Es uno solo: el diagnostico no deja publicar un pool con dos
     * inicios sin mensaje (E-15), asi que aqui no hay que elegir.
     */
    Optional<NodoDeLaVersion> inicioAMano() {
        return nodos.values().stream()
                .filter(nodo -> nodo.esEventoDe(TipoEvento.INICIO))
                .findFirst();
    }

    /** El evento por el que un mensaje abre un caso. Lo usara la mensajeria. */
    Optional<NodoDeLaVersion> inicioPorMensaje() {
        return nodos.values().stream()
                .filter(nodo -> nodo.esEventoDe(TipoEvento.MENSAJE_INICIO))
                .findFirst();
    }

    /** Las salidas de un nodo, en el orden en que un gateway las evalua. */
    List<ArcoDeLaVersion> salidasDe(Long nodoId) {
        return salidas.getOrDefault(nodoId, List.of());
    }

    List<ArcoDeLaVersion> entradasDe(Long nodoId) {
        return entradas.getOrDefault(nodoId, List.of());
    }

    /** Si desde un nodo se puede llegar a otro siguiendo los arcos. */
    boolean alcanza(Long desde, Long hasta) {
        return alcanzablesDesde.getOrDefault(desde, Set.of()).contains(hasta);
    }

    /** El nombre del mensaje que un nodo espera, si es de los que esperan uno. */
    Optional<String> mensajeQueEspera(Long nodoId) {
        return Optional.ofNullable(mensajesQueEsperan.get(nodoId));
    }
}
