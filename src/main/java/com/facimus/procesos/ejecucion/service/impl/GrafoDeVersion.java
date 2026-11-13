package com.facimus.procesos.ejecucion.service.impl;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.condiciones.Condicion;
import com.facimus.procesos.common.condiciones.CondicionMalEscrita;
import com.facimus.procesos.common.condiciones.EvaluadorDeCondiciones;
import com.facimus.procesos.ejecucion.model.TipoNodoCaso;
import com.facimus.procesos.modelado.dto.response.ActividadResponse;
import com.facimus.procesos.modelado.dto.response.ArcoResponse;
import com.facimus.procesos.modelado.dto.response.AsignacionResponse;
import com.facimus.procesos.modelado.dto.response.CorrelacionResponse;
import com.facimus.procesos.modelado.dto.response.DiagramaResponse;
import com.facimus.procesos.modelado.dto.response.EventoResponse;
import com.facimus.procesos.modelado.dto.response.FormularioResponse;
import com.facimus.procesos.modelado.dto.response.GatewayResponse;
import com.facimus.procesos.modelado.dto.response.LaneResponse;
import com.facimus.procesos.modelado.dto.response.MensajeResponse;
import com.facimus.procesos.modelado.dto.response.PoolResponse;
import com.facimus.procesos.modelado.model.Integracion;
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
 * siempre da el mismo grafo. Por eso se guarda en memoria en vez de armarse en cada peticion (D19), y por eso lo
 * comparten los hilos que atienden peticiones: nada de lo que hay dentro cambia despues de armarlo, ni las listas
 * de arcos, que se ordenan antes de entrar y ya no se vuelven a tocar.
 */
final class GrafoDeVersion {

    private final Long poolDeLaTienda;
    private final Map<Long, NodoDeLaVersion> nodos;
    private final Map<Long, List<ArcoDeLaVersion>> salidas;
    private final Map<Long, List<ArcoDeLaVersion>> entradas;
    private final List<MensajeDeLaVersion> mensajes;
    private final Map<Long, Set<Long>> alcanzablesDesde;
    private final boolean ejecutable;
    private final Map<Long, TemporizadorDeLaVersion> temporizadores;
    private final Map<Long, FormularioResponse> formularios;
    private final Map<Long, AsignacionResponse> asignaciones;

    private GrafoDeVersion(Long poolDeLaTienda, Map<Long, NodoDeLaVersion> nodos, ArcosDelPool arcos,
            List<MensajeDeLaVersion> mensajes, boolean ejecutable, Map<Long, TemporizadorDeLaVersion> temporizadores,
            Map<Long, FormularioResponse> formularios, Map<Long, AsignacionResponse> asignaciones) {
        this.ejecutable = ejecutable;
        this.asignaciones = Collections.unmodifiableMap(asignaciones);
        this.temporizadores = Collections.unmodifiableMap(temporizadores);
        this.formularios = Collections.unmodifiableMap(formularios);
        this.poolDeLaTienda = poolDeLaTienda;
        this.nodos = Collections.unmodifiableMap(nodos);
        this.salidas = sinPoderTocarlo(arcos.salidas());
        this.entradas = sinPoderTocarlo(arcos.entradas());
        this.mensajes = mensajes;
        this.alcanzablesDesde = alcanzables(nodos.keySet(), arcos.salidas());
    }

    /** Los arcos entre nodos del pool, por el nodo del que salen y por el nodo al que entran. */
    private record ArcosDelPool(Map<Long, List<ArcoDeLaVersion>> salidas, Map<Long, List<ArcoDeLaVersion>> entradas) {
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
        return new GrafoDeVersion(pool, nodos, arcosDelPool(diagrama, nodos), mensajesDelDiagrama(diagrama),
                diagrama.proceso().modo().ejecuta(), temporizadoresDelPool(diagrama, nodos),
                formulariosDelPool(diagrama, nodos), asignacionesDelPool(diagrama, nodos));
    }

    /** Los arcos que unen dos nodos del pool, con su condicion compilada; las salidas de cada nodo, por su orden. */
    private static ArcosDelPool arcosDelPool(DiagramaResponse diagrama, Map<Long, NodoDeLaVersion> nodos) {
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
        return new ArcosDelPool(salidas, entradas);
    }

    /**
     * PR 70: a quien le llega cada tarea de usuario del pool de la tienda, como la congelo la version. La que no trae
     * ninguna espera en la bandeja de su rol; una instantanea de antes no trae ninguna.
     */
    private static Map<Long, AsignacionResponse> asignacionesDelPool(DiagramaResponse diagrama,
            Map<Long, NodoDeLaVersion> nodos) {
        Map<Long, AsignacionResponse> asignaciones = new HashMap<>();
        for (ActividadResponse actividad : diagrama.actividades()) {
            if (actividad.asignacion() != null && nodos.containsKey(actividad.id())
                    && nodos.get(actividad.id()).esTareaDeUsuario()) {
                asignaciones.put(actividad.id(), actividad.asignacion());
            }
        }
        return asignaciones;
    }

    /** PR 70: a quien le llega una tarea de usuario, si la version le dio a alguien; vacio es la bandeja del rol. */
    Optional<AsignacionResponse> asignacionDe(Long nodoId) {
        return Optional.ofNullable(asignaciones.get(nodoId));
    }

    /**
     * PR 69: los formularios congelados en la version, de las tareas de usuario y de los inicios del pool de la tienda.
     * Un nodo de otro tipo no deberia traer uno (la base no lo deja); si una instantanea lo trajera, no se pide.
     */
    private static Map<Long, FormularioResponse> formulariosDelPool(DiagramaResponse diagrama,
            Map<Long, NodoDeLaVersion> nodos) {
        Map<Long, FormularioResponse> formularios = new HashMap<>();
        for (ActividadResponse actividad : diagrama.actividades()) {
            if (actividad.formulario() != null && nodos.containsKey(actividad.id())
                    && nodos.get(actividad.id()).esTareaDeUsuario()) {
                formularios.put(actividad.id(), actividad.formulario());
            }
        }
        for (EventoResponse evento : diagrama.eventos()) {
            if (evento.formulario() != null && nodos.containsKey(evento.id())
                    && nodos.get(evento.id()).esEventoDe(TipoEvento.INICIO)) {
                formularios.put(evento.id(), evento.formulario());
            }
        }
        return formularios;
    }

    /** PR 69: el formulario de una tarea de usuario o del inicio, si la version le congelo uno. */
    Optional<FormularioResponse> formularioDe(Long nodoId) {
        return Optional.ofNullable(formularios.get(nodoId));
    }

    /** PR 68: los eventos de temporizador del pool de la tienda, con lo que dicen de cuando saltan. */
    private static Map<Long, TemporizadorDeLaVersion> temporizadoresDelPool(DiagramaResponse diagrama,
            Map<Long, NodoDeLaVersion> nodos) {
        Map<Long, TemporizadorDeLaVersion> temporizadores = new LinkedHashMap<>();
        for (EventoResponse evento : diagrama.eventos()) {
            if (evento.tipoEvento().esTemporizador() && nodos.containsKey(evento.id())) {
                temporizadores.put(evento.id(), new TemporizadorDeLaVersion(evento.id(), evento.nombre(),
                        evento.tipoEvento(), evento.tipoTemporizador(), evento.temporizador(), evento.adjuntoAId(),
                        !Boolean.FALSE.equals(evento.interrumpe())));
            }
        }
        return temporizadores;
    }

    /**
     * PR 55: el modo con el que se publico la version. Una version documental se lee y se documenta, pero no abre
     * casos ni recibe mensajes; una instantanea de antes no trae modo, y es ejecutable, como eran todas.
     */
    boolean ejecutable() {
        return ejecutable;
    }

    /** El mismo grafo, si la version se ejecuta; si es documental, la regla que lo dice. */
    GrafoDeVersion queSeEjecute() {
        if (!ejecutable) {
            throw new ReglaNegocioException("La versión vigente del proceso es documental: se publica y se "
                    + "documenta, pero no abre casos ni recibe mensajes.");
        }
        return this;
    }

    /**
     * Los mensajes de la version, con lo que cada uno necesita para enviarse o para encontrar su caso: el
     * participante del otro lado con su clase de socio, la clave de correlacion y con que mensaje se contesta.
     *
     * <p>Entran todos los del proceso, no solo los anclados: un entrante llega diciendo su nombre, y hay que poder
     * reconocerlo aunque el nodo que lo espera se haya quedado sin dibujar.
     */
    private static List<MensajeDeLaVersion> mensajesDelDiagrama(DiagramaResponse diagrama) {
        Map<Long, String> nombreDePool = new HashMap<>();
        Map<Long, Integracion> integracionDePool = new HashMap<>();
        for (PoolResponse pool : diagrama.pools()) {
            nombreDePool.put(pool.id(), pool.nombre());
            integracionDePool.put(pool.id(), pool.integracion());
        }
        Map<Long, CorrelacionResponse> correlaciones = new HashMap<>();
        diagrama.correlaciones().forEach(clave -> correlaciones.putIfAbsent(clave.mensajeId(), clave));
        Map<Long, String> nombreDeMensaje = new HashMap<>();
        diagrama.mensajes().forEach(mensaje -> nombreDeMensaje.put(mensaje.id(), mensaje.nombre()));

        List<MensajeDeLaVersion> mensajes = new ArrayList<>();
        for (MensajeResponse mensaje : diagrama.mensajes()) {
            CorrelacionResponse clave = correlaciones.get(mensaje.id());
            mensajes.add(new MensajeDeLaVersion(mensaje.id(), mensaje.nombre(), mensaje.nodoOrigenId(),
                    mensaje.nodoDestinoId(), mensaje.poolOrigenId(), mensaje.poolDestinoId(),
                    nombreDePool.get(mensaje.poolDestinoId()), integracionDePool.get(mensaje.poolDestinoId()),
                    mensaje.tipoDestino(), mensaje.siFalla(), mensaje.nodoManejoErrorId(),
                    mensaje.campos() == null ? List.of() : mensaje.campos(), mensaje.variable(),
                    nombreDeMensaje.get(mensaje.respuestaEsperadaId()), clave == null ? null : clave.campo(),
                    clave == null ? null : clave.sinCaso(), mensaje.origenExterno()));
        }
        return List.copyOf(mensajes);
    }

    /**
     * Lo mismo que llego, pero sin manera de cambiarlo: el grafo se comparte entre hilos y una lista de arcos que
     * alguien reordenara por su cuenta cambiaria por donde se va un gateway en los casos de los demas. Conserva el
     * orden de insercion, que en los nodos es el que decide cual es el inicio.
     */
    private static Map<Long, List<ArcoDeLaVersion>> sinPoderTocarlo(Map<Long, List<ArcoDeLaVersion>> arcos) {
        Map<Long, List<ArcoDeLaVersion>> copia = new LinkedHashMap<>();
        arcos.forEach((nodoId, lista) -> copia.put(nodoId, List.copyOf(lista)));
        return Collections.unmodifiableMap(copia);
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
        } catch (CondicionMalEscrita _) {
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
            alcance.put(nodo, Set.copyOf(vistos));
        }
        return Collections.unmodifiableMap(alcance);
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

    /**
     * PR 68: por donde empieza una prueba del borrador: el inicio a mano, el temporizado (la prueba no espera a su
     * hora) o el de mensaje (las variables de la prueba hacen de cuerpo del mensaje), en ese orden.
     */
    Optional<NodoDeLaVersion> inicioDeUnaPrueba() {
        return inicioAMano()
                .or(() -> nodos.values().stream().filter(nodo -> nodo.esEventoDe(TipoEvento.TEMPORIZADOR_INICIO))
                        .findFirst())
                .or(this::inicioPorMensaje);
    }

    /** PR 68: el temporizador de un evento, si lo es y dice cuando salta. */
    Optional<TemporizadorDeLaVersion> temporizador(Long nodoId) {
        return Optional.ofNullable(temporizadores.get(nodoId)).filter(TemporizadorDeLaVersion::diceCuando);
    }

    /** PR 68: los temporizadores de borde que cuelgan de una actividad y dicen cuando saltan. */
    List<TemporizadorDeLaVersion> bordesDe(Long actividadId) {
        return temporizadores.values().stream()
                .filter(temporizador -> temporizador.evento() == TipoEvento.TEMPORIZADOR_BORDE)
                .filter(temporizador -> actividadId.equals(temporizador.adjuntoAId()))
                .filter(TemporizadorDeLaVersion::diceCuando)
                .toList();
    }

    /** PR 68: los inicios temporizados que dicen cuando saltan: los que abren casos solos. */
    List<TemporizadorDeLaVersion> iniciosTemporizados() {
        return temporizadores.values().stream()
                .filter(temporizador -> temporizador.evento() == TipoEvento.TEMPORIZADOR_INICIO)
                .filter(TemporizadorDeLaVersion::diceCuando)
                .toList();
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

    /** El mensaje que un nodo espera, si es de los que esperan uno. */
    Optional<MensajeDeLaVersion> mensajeQueEspera(Long nodoId) {
        return mensajes.stream()
                .filter(mensaje -> nodoId.equals(mensaje.nodoDestinoId()))
                .findFirst();
    }

    /**
     * El mensaje que un nodo manda al pasar por el. Es lo que convierte una actividad de envio en un envio y no en
     * un paso que se completa solo: sin mensaje anclado no hay a quien mandarle nada.
     */
    Optional<MensajeDeLaVersion> mensajeQueManda(Long nodoId) {
        return mensajes.stream()
                .filter(mensaje -> nodoId.equals(mensaje.nodoOrigenId()))
                .findFirst();
    }

    /**
     * Lo que el participante del otro lado de un mensaje manda por su cuenta, sin que nadie se lo pida: la
     * confirmacion de entrega del transportista es uno. Son los mensajes de origen externo que salen de su pool.
     */
    Optional<MensajeDeLaVersion> avisoDe(MensajeDeLaVersion saliente) {
        return mensajes.stream()
                .filter(MensajeDeLaVersion::origenExterno)
                .filter(aviso -> aviso.poolOrigenId() != null
                        && aviso.poolOrigenId().equals(saliente.poolDestinoId()))
                .findFirst();
    }

    /** El mensaje que se llama asi. Es por donde entra uno que llega, que trae su nombre y no un id. */
    Optional<MensajeDeLaVersion> mensajePorNombre(String nombre) {
        return mensajes.stream()
                .filter(mensaje -> mensaje.nombre().equals(nombre))
                .findFirst();
    }
}
