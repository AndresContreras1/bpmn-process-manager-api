package com.facimus.procesos.modelado.service.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.common.SinPermisoException;
import com.facimus.procesos.common.model.Empresa;
import com.facimus.procesos.common.model.RolAcceso;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.model.RolProceso;
import com.facimus.procesos.gestion.repository.RolProcesoRepository;
import com.facimus.procesos.gestion.service.ConfiguracionTiendaService;
import com.facimus.procesos.gestion.service.RolProcesoService;
import com.facimus.procesos.modelado.bpmn.PlanBpmn;
import com.facimus.procesos.modelado.model.Actividad;
import com.facimus.procesos.modelado.model.Arco;
import com.facimus.procesos.modelado.model.Artefacto;
import com.facimus.procesos.modelado.model.Asociacion;
import com.facimus.procesos.modelado.model.Correlacion;
import com.facimus.procesos.modelado.model.Evento;
import com.facimus.procesos.modelado.model.ExtensionXml;
import com.facimus.procesos.modelado.model.Gateway;
import com.facimus.procesos.modelado.model.Lane;
import com.facimus.procesos.modelado.model.Mensaje;
import com.facimus.procesos.modelado.model.NodoFlujo;
import com.facimus.procesos.modelado.model.Pool;
import com.facimus.procesos.modelado.model.TipoParticipante;
import com.facimus.procesos.modelado.repository.ActividadRepository;
import com.facimus.procesos.modelado.repository.ArcoRepository;
import com.facimus.procesos.modelado.repository.ArtefactoRepository;
import com.facimus.procesos.modelado.repository.AsociacionRepository;
import com.facimus.procesos.modelado.repository.CorrelacionRepository;
import com.facimus.procesos.modelado.repository.EventoRepository;
import com.facimus.procesos.modelado.repository.ExtensionXmlRepository;
import com.facimus.procesos.modelado.repository.GatewayRepository;
import com.facimus.procesos.modelado.repository.LaneRepository;
import com.facimus.procesos.modelado.repository.MensajeRepository;
import com.facimus.procesos.modelado.repository.PoolRepository;

import lombok.RequiredArgsConstructor;

/**
 * PR 54: un {@link PlanBpmn} sobre el borrador de un proceso, por diferencias: cada elemento se busca por su bpmn_id y
 * se crea, se edita o se da de baja. Lo que no cambio no se toca, asi que importar el mismo archivo dos veces no
 * escribe nada la segunda, y la version optimista de lo que nadie toco no sube.
 *
 * <p>El pool de la organizacion es siempre el que el proceso ya tiene: el primero del plan cae en el, con el
 * bpmn_id de siempre. Antes de escribir se comprueba todo lo que puede impedirlo: la politica de estructura de la
 * tienda (D16) si cambian pools o lanes, ser administrador si algo se da de baja (como los DELETE), y los roles de
 * las lanes, que un administrador crea al importar y un editor no.
 */
@Component
@RequiredArgsConstructor
class AplicacionDelModelo {

    private final PoolRepository poolRepository;
    private final LaneRepository laneRepository;
    private final ActividadRepository actividadRepository;
    private final GatewayRepository gatewayRepository;
    private final EventoRepository eventoRepository;
    private final ArcoRepository arcoRepository;
    private final MensajeRepository mensajeRepository;
    private final CorrelacionRepository correlacionRepository;
    private final ExtensionXmlRepository extensionXmlRepository;
    private final ArtefactoRepository artefactoRepository;
    private final AsociacionRepository asociacionRepository;
    private final RolProcesoRepository rolProcesoRepository;
    private final RolProcesoService rolProcesoService;
    private final ConfiguracionTiendaService configuracionTiendaService;

    /** La clave del marco de los artefactos sueltos, que no estan en ninguna lane. */
    private static final String SUELTOS = "#sueltos";

    /** Cuanto cambio el borrador. */
    record Cambios(int creados, int editados, int dadosDeBaja) {

        int total() {
            return creados + editados + dadosDeBaja;
        }
    }

    @Transactional
    Cambios aplicar(Long empresaId, Long usuarioId, RolAcceso rol, Proceso proceso, PlanBpmn plan) {
        return new Aplicacion(empresaId, usuarioId, rol, proceso, plan).aplicar();
    }

    /** Una aplicacion: el borrador tal como esta, el plan y lo que se va contando. */
    private final class Aplicacion {

        private final Long empresaId;
        private final Long usuarioId;
        private final RolAcceso rol;
        private final Proceso proceso;
        private final Empresa empresa;
        private final PlanBpmn plan;
        private final Map<String, Pool> pools;
        private final Map<String, Lane> lanes;
        private final Map<String, NodoFlujo> nodos;
        private final Map<String, Arco> arcos;
        private final Map<String, Mensaje> mensajes;
        private final Map<String, Artefacto> artefactos;
        private final Map<String, Asociacion> asociaciones;
        /** Los nodos que esta importacion crea: pegarlos a su actividad no cuenta como una edicion. */
        private final Set<String> nodosNuevos = new HashSet<>();
        /** El bpmn_id del pool de la organizacion en el plan, y el que tiene en el borrador. */
        private final String organizacionEnElPlan;
        private final String organizacion;
        /** Los mensajes que esta importacion crea: su respuesta no cuenta como una edicion. */
        private final Set<String> mensajesNuevos = new HashSet<>();
        private int creados;
        private int editados;
        private int dadosDeBaja;

        Aplicacion(Long empresaId, Long usuarioId, RolAcceso rol, Proceso proceso, PlanBpmn plan) {
            this.empresaId = empresaId;
            this.usuarioId = usuarioId;
            this.rol = rol;
            this.proceso = proceso;
            this.empresa = proceso.getEmpresa();
            this.plan = plan;
            Long procesoId = proceso.getId();
            this.pools = porBpmnId(poolRepository.findAllByProcesoIdAndEmpresaIdOrderByOrdenAsc(procesoId, empresaId),
                    Pool::getBpmnId);
            this.lanes = porBpmnId(laneRepository.delProcesoEnOrden(procesoId, empresaId), Lane::getBpmnId);
            this.nodos = porBpmnId(Stream.of(
                            actividadRepository.findAllByLane_Pool_ProcesoIdAndEmpresaIdOrderByIdAsc(procesoId, empresaId),
                            gatewayRepository.findAllByLane_Pool_ProcesoIdAndEmpresaIdOrderByIdAsc(procesoId, empresaId),
                            eventoRepository.findAllByLane_Pool_ProcesoIdAndEmpresaIdOrderByIdAsc(procesoId, empresaId))
                    .flatMap(List::stream).map(NodoFlujo.class::cast).toList(), NodoFlujo::getBpmnId);
            this.arcos = porBpmnId(arcoRepository.findAllByPool_ProcesoIdAndEmpresaIdOrderByIdAsc(procesoId, empresaId),
                    Arco::getBpmnId);
            this.mensajes = porBpmnId(mensajeRepository.findAllByProcesoIdAndEmpresaIdOrderByIdAsc(procesoId, empresaId),
                    Mensaje::getBpmnId);
            this.artefactos = porBpmnId(artefactoRepository.findAllByProcesoIdAndEmpresaIdOrderByIdAsc(procesoId,
                    empresaId), Artefacto::getBpmnId);
            this.asociaciones = porBpmnId(asociacionRepository.findAllByProcesoIdAndEmpresaIdOrderByIdAsc(procesoId,
                    empresaId), Asociacion::getBpmnId);
            this.organizacionEnElPlan = plan.pools().getFirst().bpmnId();
            this.organizacion = pools.values().stream()
                    .filter(pool -> pool.getTipoParticipante() == TipoParticipante.EMPRESA)
                    .map(Pool::getBpmnId).findFirst().orElseThrow();
        }

        Cambios aplicar() {
            Map<String, RolProceso> roles = comprobar();
            Set<String> mensajesDelPlan = ids(plan.mensajes().stream().map(PlanBpmn.Mensaje::bpmnId));
            Set<String> arcosDelPlan = ids(plan.arcos().stream().map(PlanBpmn.Arco::bpmnId));
            // Primero lo que cuelga de los nodos, para que nada apunte a un nodo que se va
            darDeBajaLoDescriptivoQueSobra();
            for (Mensaje mensaje : List.copyOf(mensajes.values())) {
                if (!mensajesDelPlan.contains(mensaje.getBpmnId()) || cambiaDePools(mensaje)) {
                    darDeBaja(mensaje);
                }
            }
            for (Arco arco : List.copyOf(arcos.values())) {
                if (!arcosDelPlan.contains(arco.getBpmnId())) {
                    arcoRepository.delete(arco);
                    arcos.remove(arco.getBpmnId());
                    dadosDeBaja++;
                }
            }
            plan.pools().forEach(this::pool);
            plan.lanes().forEach(lane -> lane(lane, roles));
            Map<String, int[]> desplazamientos = desplazamientos();
            plan.nodos().forEach(nodo -> nodo(nodo, desplazamientos));
            plan.nodos().forEach(this::pegar);
            plan.arcos().forEach(this::arco);
            plan.mensajes().forEach(this::mensaje);
            plan.mensajes().forEach(this::respuestaYCorrelacion);
            plan.artefactos().forEach(artefacto -> artefacto(artefacto, desplazamientos));
            plan.asociaciones().forEach(this::asociacion);
            modo();
            darDeBajaLoQueSobra();
            extensiones();
            return new Cambios(creados, editados, dadosDeBaja);
        }

        // ---------- Antes de escribir ----------

        /** Todo lo que puede impedir la importacion, antes de tocar nada; devuelve el rol de cada lane. */
        private Map<String, RolProceso> comprobar() {
            boolean estructura = plan.pools().stream().anyMatch(this::poolCambia)
                    || plan.lanes().stream().anyMatch(this::laneCambia)
                    || pools.keySet().stream().anyMatch(pool -> !pool.equals(organizacion) && !enElPlan(pool,
                            plan.pools().stream().map(this::id)))
                    || lanes.keySet().stream().anyMatch(lane -> !enElPlan(lane, plan.lanes().stream()
                            .map(PlanBpmn.Lane::bpmnId)));
            if (estructura) {
                configuracionTiendaService.exigirPuedeEditarEstructura(empresaId, usuarioId);
            }
            int bajas = sobran(pools.keySet().stream().filter(pool -> !pool.equals(organizacion)),
                    plan.pools().stream().map(this::id))
                    + sobran(lanes.keySet().stream(), plan.lanes().stream().map(PlanBpmn.Lane::bpmnId))
                    + sobran(nodos.keySet().stream(), plan.nodos().stream().map(PlanBpmn.Nodo::bpmnId))
                    + sobran(arcos.keySet().stream(), plan.arcos().stream().map(PlanBpmn.Arco::bpmnId))
                    + sobran(mensajes.keySet().stream(), plan.mensajes().stream().map(PlanBpmn.Mensaje::bpmnId))
                    + sobran(artefactos.keySet().stream(), plan.artefactos().stream().map(PlanBpmn.Artefacto::bpmnId))
                    + sobran(asociaciones.keySet().stream(),
                            plan.asociaciones().stream().map(PlanBpmn.Asociacion::bpmnId));
            if (bajas > 0 && rol != RolAcceso.ADMINISTRADOR) {
                throw new SinPermisoException("El archivo deja fuera " + bajas + (bajas == 1 ? " elemento" : " elementos")
                        + " del borrador, y dar de baja es de un administrador. Importa un archivo que los tenga, o "
                        + "pide a un administrador que lo importe.");
            }
            return roles();
        }

        /** El rol de cada nombre de lane; los que faltan los crea un administrador, y un editor no puede seguir. */
        private Map<String, RolProceso> roles() {
            Set<String> nombres = new LinkedHashSet<>();
            plan.lanes().forEach(lane -> nombres.add(lane.rol()));
            Map<String, RolProceso> roles = new HashMap<>();
            List<String> faltan = new ArrayList<>();
            for (String nombre : nombres) {
                rolProcesoRepository.findFirstByEmpresaIdAndNombreIgnoreCaseAndActivoTrueOrderByIdAsc(empresaId, nombre)
                        .ifPresentOrElse(encontrado -> roles.put(nombre, encontrado), () -> faltan.add(nombre));
            }
            if (!faltan.isEmpty() && rol != RolAcceso.ADMINISTRADOR) {
                throw new SinPermisoException("Las lanes del archivo piden roles que la organizacion no tiene: "
                        + String.join(", ", faltan) + ". Solo un administrador puede crearlos: que los cree, o que "
                        + "importe el archivo.");
            }
            for (String nombre : faltan) {
                Long id = rolProcesoService.crear(empresaId, usuarioId, nombre, "Creado al importar un archivo BPMN.")
                        .id();
                roles.put(nombre, rolProcesoRepository.findByIdAndEmpresaIdAndActivoTrue(id, empresaId).orElseThrow());
            }
            return roles;
        }

        private boolean poolCambia(PlanBpmn.Pool deseado) {
            Pool actual = pools.get(id(deseado));
            return actual == null || deseado.nombre() != null && !Objects.equals(actual.getNombre(), deseado.nombre())
                    || actual.getTipoParticipante() != deseado.tipo() || actual.getIntegracion() != deseado.integracion()
                    || actual.getOrden() != deseado.orden() || actual.isCajaNegra() != deseado.cajaNegra();
        }

        private boolean laneCambia(PlanBpmn.Lane deseada) {
            Lane actual = lanes.get(deseada.bpmnId());
            return actual == null || !Objects.equals(actual.getNombre(), deseada.nombre())
                    || actual.getOrden() != deseada.orden() || !actual.getPool().getBpmnId().equals(idDelPool(deseada.pool()))
                    || !actual.getRolProceso().getNombre().equalsIgnoreCase(deseada.rol());
        }

        // ---------- Pools y lanes ----------

        private void pool(PlanBpmn.Pool deseado) {
            String id = id(deseado);
            Pool actual = pools.get(id);
            if (actual == null) {
                pools.put(id, poolRepository.save(Pool.builder().empresa(empresa).proceso(proceso).bpmnId(id)
                        .nombre(deseado.nombre()).tipoParticipante(deseado.tipo()).integracion(deseado.integracion())
                        .orden(deseado.orden()).cajaNegra(deseado.cajaNegra()).build()));
                creados++;
                return;
            }
            if (poolCambia(deseado)) {
                if (deseado.nombre() != null) {
                    actual.setNombre(deseado.nombre());
                }
                if (!id.equals(organizacion)) {
                    actual.setTipoParticipante(deseado.tipo());
                    actual.setCajaNegra(deseado.cajaNegra());
                }
                actual.setIntegracion(deseado.integracion());
                actual.setOrden(deseado.orden());
                editados++;
            }
        }

        private void lane(PlanBpmn.Lane deseada, Map<String, RolProceso> roles) {
            Pool pool = pools.get(idDelPool(deseada.pool()));
            RolProceso rolDeLaLane = roles.get(deseada.rol());
            Lane actual = lanes.get(deseada.bpmnId());
            if (actual == null) {
                lanes.put(deseada.bpmnId(), laneRepository.save(Lane.builder().empresa(empresa)
                        .bpmnId(deseada.bpmnId()).nombre(deseada.nombre()).orden(deseada.orden()).pool(pool)
                        .rolProceso(rolDeLaLane).build()));
                creados++;
                return;
            }
            if (laneCambia(deseada)) {
                actual.setNombre(deseada.nombre());
                actual.setOrden(deseada.orden());
                actual.setPool(pool);
                actual.setRolProceso(rolDeLaLane);
                editados++;
            }
        }

        // ---------- Nodos ----------

        /**
         * Lo que se suma a las posiciones del plan para que queden en el mismo marco que las del borrador: la menor x
         * del borrador y la menor y de cada lane. Asi, si el dibujo no cambio, las posiciones guardadas tampoco.
         */
        private Map<String, int[]> desplazamientos() {
            Map<String, int[]> desplazamientos = new HashMap<>();
            // Un evento de borde se dibuja sobre su actividad: su posicion no marca el dibujo
            List<NodoFlujo> enElFlujo = nodos.values().stream().filter(nodo -> !esDeBorde(nodo)).toList();
            List<Artefacto> enLanes = artefactos.values().stream().filter(uno -> uno.getLane() != null).toList();
            int menorX = enElFlujo.stream().mapToInt(NodoFlujo::getPosicionX).min()
                    .orElse(enLanes.stream().mapToInt(Artefacto::getPosicionX).min().orElse(0));
            for (NodoFlujo nodo : enElFlujo) {
                desplazamientos.merge(nodo.getLane().getBpmnId(), new int[] {menorX, nodo.getPosicionY()},
                        (uno, otro) -> new int[] {menorX, Math.min(uno[1], otro[1])});
            }
            // Una lane con artefactos y sin nodos toma el marco de sus artefactos
            Set<String> conNodos = Set.copyOf(desplazamientos.keySet());
            for (Artefacto artefacto : enLanes) {
                String lane = artefacto.getLane().getBpmnId();
                if (!conNodos.contains(lane)) {
                    desplazamientos.merge(lane, new int[] {menorX, artefacto.getPosicionY()},
                            (uno, otro) -> new int[] {menorX, Math.min(uno[1], otro[1])});
                }
            }
            desplazamientos.put("", new int[] {menorX, 0});
            List<Artefacto> sueltos = artefactos.values().stream().filter(uno -> uno.getLane() == null).toList();
            desplazamientos.put(SUELTOS, new int[] {sueltos.stream().mapToInt(Artefacto::getPosicionX).min().orElse(0),
                    sueltos.stream().mapToInt(Artefacto::getPosicionY).min().orElse(0)});
            return desplazamientos;
        }

        private static boolean esDeBorde(NodoFlujo nodo) {
            return nodo instanceof Evento evento && evento.getTipoEvento().esDeBorde();
        }

        private void nodo(PlanBpmn.Nodo deseado, Map<String, int[]> desplazamientos) {
            Lane lane = lanes.get(deseado.lane());
            int[] desplazamiento = desplazamientos.getOrDefault(deseado.lane(), desplazamientos.get(""));
            int x = deseado.x() + desplazamiento[0];
            int y = deseado.y() + desplazamiento[1];
            NodoFlujo actual = nodos.get(deseado.bpmnId());
            if (actual != null && clase(actual) != deseado.clase()) {
                // Un id que paso de tarea a evento, por ejemplo: es otro nodo que hereda el nombre
                darDeBaja(actual);
                actual = null;
            }
            if (actual == null) {
                NodoFlujo nuevo = switch (deseado.clase()) {
                    case ACTIVIDAD -> actividadRepository.save(Actividad.builder().empresa(empresa)
                            .bpmnId(deseado.bpmnId()).nombre(deseado.nombre()).descripcion(deseado.descripcion())
                            .tipoActividad(deseado.tipoActividad()).posicionX(x).posicionY(y).lane(lane).build());
                    case GATEWAY -> gatewayRepository.save(Gateway.builder().empresa(empresa).bpmnId(deseado.bpmnId())
                            .nombre(deseado.nombre()).tipoGateway(deseado.tipoGateway()).posicionX(x).posicionY(y)
                            .lane(lane).build());
                    case EVENTO -> eventoRepository.save(Evento.builder().empresa(empresa).bpmnId(deseado.bpmnId())
                            .nombre(deseado.nombre()).tipoEvento(deseado.tipoEvento()).posicionX(x).posicionY(y)
                            .lane(lane).tipoTemporizador(deseado.tipoTemporizador())
                            .temporizador(deseado.temporizador()).interrumpe(deseado.interrumpe()).build());
                };
                nodos.put(deseado.bpmnId(), nuevo);
                nodosNuevos.add(deseado.bpmnId());
                creados++;
                return;
            }
            // La posicion de un evento de borde no dice nada: se dibuja sobre su actividad
            boolean sinPosicion = deseado.adjuntoA() != null;
            if (sinPosicion) {
                x = actual.getPosicionX();
                y = actual.getPosicionY();
            }
            boolean cambia = !Objects.equals(actual.getNombre(), deseado.nombre()) || actual.getPosicionX() != x
                    || actual.getPosicionY() != y || !actual.getLane().getBpmnId().equals(deseado.lane());
            switch (actual) {
                case Actividad actividad -> {
                    cambia |= actividad.getTipoActividad() != deseado.tipoActividad()
                            || !Objects.equals(actividad.getDescripcion(), deseado.descripcion());
                    if (cambia) {
                        actividad.setTipoActividad(deseado.tipoActividad());
                        actividad.setDescripcion(deseado.descripcion());
                    }
                }
                case Gateway gateway -> {
                    cambia |= gateway.getTipoGateway() != deseado.tipoGateway();
                    gateway.setTipoGateway(deseado.tipoGateway());
                }
                case Evento evento -> {
                    cambia |= evento.getTipoEvento() != deseado.tipoEvento()
                            || evento.getTipoTemporizador() != deseado.tipoTemporizador()
                            || !Objects.equals(evento.getTemporizador(), deseado.temporizador())
                            || !Objects.equals(evento.getInterrumpe(), deseado.interrumpe());
                    evento.setTipoEvento(deseado.tipoEvento());
                    evento.setTipoTemporizador(deseado.tipoTemporizador());
                    evento.setTemporizador(deseado.temporizador());
                    evento.setInterrumpe(deseado.interrumpe());
                }
                default -> throw new IllegalStateException("Un nodo de un tipo que no existe: " + actual.getBpmnId());
            }
            if (cambia) {
                actual.setNombre(deseado.nombre());
                actual.setPosicionX(x);
                actual.setPosicionY(y);
                actual.setLane(lane);
                editados++;
            }
        }

        /** PR 55: un evento de borde con su actividad, cuando ya estan todos los nodos. */
        private void pegar(PlanBpmn.Nodo deseado) {
            if (nodos.get(deseado.bpmnId()) instanceof Evento evento) {
                NodoFlujo actividad = deseado.adjuntoA() == null ? null : nodos.get(deseado.adjuntoA());
                if (evento.getAdjuntoA() != actividad) {
                    evento.setAdjuntoA(actividad);
                    if (!nodosNuevos.contains(deseado.bpmnId())) {
                        editados++;
                    }
                }
            }
        }

        // ---------- Lo descriptivo ----------

        /** Lo que sobra se va antes que los nodos: una asociacion nombra a sus extremos. */
        private void darDeBajaLoDescriptivoQueSobra() {
            Set<String> asociacionesDelPlan = ids(plan.asociaciones().stream().map(PlanBpmn.Asociacion::bpmnId));
            Set<String> artefactosDelPlan = ids(plan.artefactos().stream().map(PlanBpmn.Artefacto::bpmnId));
            for (Asociacion asociacion : List.copyOf(asociaciones.values())) {
                if (!asociacionesDelPlan.contains(asociacion.getBpmnId())) {
                    asociacionRepository.delete(asociacion);
                    asociaciones.remove(asociacion.getBpmnId());
                    dadosDeBaja++;
                }
            }
            for (Artefacto artefacto : List.copyOf(artefactos.values())) {
                if (!artefactosDelPlan.contains(artefacto.getBpmnId())) {
                    artefactoRepository.delete(artefacto);
                    artefactos.remove(artefacto.getBpmnId());
                    dadosDeBaja++;
                }
            }
        }

        private void artefacto(PlanBpmn.Artefacto deseado, Map<String, int[]> desplazamientos) {
            Pool pool = deseado.pool() == null ? null : pools.get(idDelPool(deseado.pool()));
            Lane lane = deseado.lane() == null ? null : lanes.get(deseado.lane());
            int[] desplazamiento = deseado.lane() == null ? desplazamientos.get(SUELTOS)
                    : desplazamientos.getOrDefault(deseado.lane(), desplazamientos.get(""));
            int x = deseado.x() + desplazamiento[0];
            int y = deseado.y() + desplazamiento[1];
            Artefacto actual = artefactos.get(deseado.bpmnId());
            if (actual == null) {
                artefactos.put(deseado.bpmnId(), artefactoRepository.save(Artefacto.builder().empresa(empresa)
                        .proceso(proceso).bpmnId(deseado.bpmnId()).tipo(deseado.tipo()).texto(deseado.texto())
                        .pool(pool).lane(lane).posicionX(x).posicionY(y).ancho(deseado.ancho()).alto(deseado.alto())
                        .build()));
                creados++;
                return;
            }
            boolean cambia = actual.getTipo() != deseado.tipo() || !Objects.equals(actual.getTexto(), deseado.texto())
                    || actual.getPool() != pool || actual.getLane() != lane || actual.getPosicionX() != x
                    || actual.getPosicionY() != y || actual.getAncho() != deseado.ancho()
                    || actual.getAlto() != deseado.alto();
            if (cambia) {
                actual.setTipo(deseado.tipo());
                actual.setTexto(deseado.texto());
                actual.setPool(pool);
                actual.setLane(lane);
                actual.setPosicionX(x);
                actual.setPosicionY(y);
                actual.setAncho(deseado.ancho());
                actual.setAlto(deseado.alto());
                editados++;
            }
        }

        private void asociacion(PlanBpmn.Asociacion deseada) {
            Asociacion actual = asociaciones.get(deseada.bpmnId());
            if (actual == null) {
                asociaciones.put(deseada.bpmnId(), asociacionRepository.save(Asociacion.builder().empresa(empresa)
                        .proceso(proceso).bpmnId(deseada.bpmnId()).tipo(deseada.tipo()).origen(deseada.origen())
                        .destino(deseada.destino()).direccion(deseada.direccion()).build()));
                creados++;
                return;
            }
            boolean cambia = actual.getTipo() != deseada.tipo() || !actual.getOrigen().equals(deseada.origen())
                    || !actual.getDestino().equals(deseada.destino()) || actual.getDireccion() != deseada.direccion();
            if (cambia) {
                actual.setTipo(deseada.tipo());
                actual.setOrigen(deseada.origen());
                actual.setDestino(deseada.destino());
                actual.setDireccion(deseada.direccion());
                editados++;
            }
        }

        /** El modo que dice el archivo; si no dice ninguno, el borrador se queda como esta. */
        private void modo() {
            if (plan.modo() != null && proceso.getModo() != plan.modo()) {
                proceso.setModo(plan.modo());
                editados++;
            }
        }

        // ---------- Flujos y mensajes ----------

        private void arco(PlanBpmn.Arco deseado) {
            Pool pool = pools.get(idDelPool(deseado.pool()));
            NodoFlujo origen = nodos.get(deseado.origen());
            NodoFlujo destino = nodos.get(deseado.destino());
            Arco actual = arcos.get(deseado.bpmnId());
            if (actual == null) {
                arcos.put(deseado.bpmnId(), arcoRepository.save(Arco.builder().empresa(empresa)
                        .bpmnId(deseado.bpmnId()).etiqueta(deseado.etiqueta()).condicion(deseado.condicion())
                        .porDefecto(deseado.porDefecto()).orden(deseado.orden()).origen(origen).destino(destino)
                        .pool(pool).build()));
                creados++;
                return;
            }
            boolean cambia = !Objects.equals(actual.getEtiqueta(), deseado.etiqueta())
                    || !Objects.equals(actual.getCondicion(), deseado.condicion())
                    || actual.isPorDefecto() != deseado.porDefecto() || actual.getOrden() != deseado.orden()
                    || actual.getOrigen() != origen || actual.getDestino() != destino || actual.getPool() != pool;
            if (cambia) {
                actual.setEtiqueta(deseado.etiqueta());
                actual.setCondicion(deseado.condicion());
                actual.setPorDefecto(deseado.porDefecto());
                actual.setOrden(deseado.orden());
                actual.setOrigen(origen);
                actual.setDestino(destino);
                actual.setPool(pool);
                editados++;
            }
        }

        /** Los pools de un mensaje no se cambian (como en la API): si el archivo los cambia, es otro mensaje. */
        private boolean cambiaDePools(Mensaje mensaje) {
            return plan.mensajes().stream().filter(deseado -> deseado.bpmnId().equals(mensaje.getBpmnId())).findFirst()
                    .map(deseado -> !mensaje.getPoolOrigen().getBpmnId().equals(idDelPool(deseado.poolOrigen()))
                            || !mensaje.getPoolDestino().getBpmnId().equals(idDelPool(deseado.poolDestino())))
                    .orElse(false);
        }

        private void mensaje(PlanBpmn.Mensaje deseado) {
            Pool origen = pools.get(idDelPool(deseado.poolOrigen()));
            Pool destino = pools.get(idDelPool(deseado.poolDestino()));
            NodoFlujo nodoOrigen = deseado.nodoOrigen() == null ? null : nodos.get(deseado.nodoOrigen());
            NodoFlujo nodoDestino = deseado.nodoDestino() == null ? null : nodos.get(deseado.nodoDestino());
            NodoFlujo manejoError = deseado.manejoError() == null ? null : nodos.get(deseado.manejoError());
            Mensaje actual = mensajes.get(deseado.bpmnId());
            if (actual == null) {
                mensajes.put(deseado.bpmnId(), mensajeRepository.save(Mensaje.builder().empresa(empresa)
                        .bpmnId(deseado.bpmnId()).proceso(proceso).nombre(deseado.nombre())
                        .contenido(deseado.contenido()).poolOrigen(origen).poolDestino(destino)
                        .nodoOrigen(nodoOrigen).nodoDestino(nodoDestino).tipoDestino(deseado.tipoDestino())
                        .siFalla(deseado.siFalla()).nodoManejoError(manejoError)
                        .origenExterno(deseado.origenExterno()).campos(deseado.campos())
                        .usoDeLosDatos(deseado.usoDeLosDatos()).variable(deseado.variable()).build()));
                mensajesNuevos.add(deseado.bpmnId());
                creados++;
                return;
            }
            boolean cambia = !Objects.equals(actual.getNombre(), deseado.nombre())
                    || !Objects.equals(actual.getContenido(), deseado.contenido())
                    || actual.getNodoOrigen() != nodoOrigen || actual.getNodoDestino() != nodoDestino
                    || actual.getTipoDestino() != deseado.tipoDestino() || actual.getSiFalla() != deseado.siFalla()
                    || actual.getNodoManejoError() != manejoError || actual.isOrigenExterno() != deseado.origenExterno()
                    || !Objects.equals(actual.getCampos(), deseado.campos())
                    || !Objects.equals(actual.getUsoDeLosDatos(), deseado.usoDeLosDatos())
                    || !Objects.equals(actual.getVariable(), deseado.variable());
            if (cambia) {
                actual.setNombre(deseado.nombre());
                actual.setContenido(deseado.contenido());
                actual.setNodoOrigen(nodoOrigen);
                actual.setNodoDestino(nodoDestino);
                actual.setTipoDestino(deseado.tipoDestino());
                actual.setSiFalla(deseado.siFalla());
                actual.setNodoManejoError(manejoError);
                actual.setOrigenExterno(deseado.origenExterno());
                actual.setCampos(deseado.campos());
                actual.setUsoDeLosDatos(deseado.usoDeLosDatos());
                actual.setVariable(deseado.variable());
                editados++;
            }
        }

        /** La respuesta y la correlacion, cuando ya existen todos los mensajes. */
        private void respuestaYCorrelacion(PlanBpmn.Mensaje deseado) {
            Mensaje mensaje = mensajes.get(deseado.bpmnId());
            Mensaje respuesta = deseado.respuesta() == null ? null : mensajes.get(deseado.respuesta());
            if (mensaje.getRespuestaEsperada() != respuesta) {
                mensaje.setRespuestaEsperada(respuesta);
                if (!mensajesNuevos.contains(deseado.bpmnId())) {
                    editados++;
                }
            }
            Correlacion actual = mensaje.getId() == null ? null
                    : correlacionRepository.findByMensajeIdAndEmpresaId(mensaje.getId(), empresaId).orElse(null);
            PlanBpmn.Correlacion deseada = deseado.correlacion();
            if (deseada == null) {
                if (actual != null) {
                    correlacionRepository.delete(actual);
                    dadosDeBaja++;
                }
                return;
            }
            if (actual == null) {
                correlacionRepository.save(Correlacion.builder().empresa(empresa).mensaje(mensaje)
                        .criterio(deseada.criterio()).campo(deseada.campo()).sinCaso(deseada.sinCaso()).build());
                creados++;
            } else if (!Objects.equals(actual.getCriterio(), deseada.criterio())
                    || !Objects.equals(actual.getCampo(), deseada.campo()) || actual.getSinCaso() != deseada.sinCaso()) {
                actual.setCriterio(deseada.criterio());
                actual.setCampo(deseada.campo());
                actual.setSinCaso(deseada.sinCaso());
                editados++;
            }
        }

        // ---------- Bajas y extensiones ----------

        private void darDeBajaLoQueSobra() {
            Set<String> nodosDelPlan = ids(plan.nodos().stream().map(PlanBpmn.Nodo::bpmnId));
            Set<String> lanesDelPlan = ids(plan.lanes().stream().map(PlanBpmn.Lane::bpmnId));
            Set<String> poolsDelPlan = ids(plan.pools().stream().map(this::id));
            for (NodoFlujo nodo : List.copyOf(nodos.values())) {
                if (!nodosDelPlan.contains(nodo.getBpmnId())) {
                    darDeBaja(nodo);
                }
            }
            for (Lane lane : List.copyOf(lanes.values())) {
                if (!lanesDelPlan.contains(lane.getBpmnId())) {
                    laneRepository.delete(lane);
                    lanes.remove(lane.getBpmnId());
                    dadosDeBaja++;
                }
            }
            for (Pool pool : List.copyOf(pools.values())) {
                if (!poolsDelPlan.contains(pool.getBpmnId()) && !pool.getBpmnId().equals(organizacion)) {
                    poolRepository.delete(pool);
                    pools.remove(pool.getBpmnId());
                    dadosDeBaja++;
                }
            }
        }

        private void darDeBaja(NodoFlujo nodo) {
            switch (nodo) {
                case Actividad actividad -> actividadRepository.delete(actividad);
                case Gateway gateway -> gatewayRepository.delete(gateway);
                case Evento evento -> eventoRepository.delete(evento);
                default -> throw new IllegalStateException("Un nodo de un tipo que no existe: " + nodo.getBpmnId());
            }
            nodos.remove(nodo.getBpmnId());
            dadosDeBaja++;
        }

        private void darDeBaja(Mensaje mensaje) {
            correlacionRepository.findByMensajeIdAndEmpresaId(mensaje.getId(), empresaId)
                    .ifPresent(correlacionRepository::delete);
            mensajeRepository.findAllByProcesoIdAndEmpresaIdOrderByIdAsc(proceso.getId(), empresaId).stream()
                    .filter(otro -> otro.getRespuestaEsperada() == mensaje)
                    .forEach(otro -> otro.setRespuestaEsperada(null));
            mensajeRepository.delete(mensaje);
            mensajes.remove(mensaje.getBpmnId());
            dadosDeBaja++;
        }

        /** Lo ajeno del archivo reemplaza lo del anterior: el pool de la organizacion, con su id de siempre. */
        private void extensiones() {
            extensionXmlRepository.borrarDelProceso(proceso.getId(), empresaId);
            List<ExtensionXml> nuevas = new ArrayList<>();
            plan.extensiones().forEach((clave, xml) -> nuevas.add(ExtensionXml.builder().empresa(empresa)
                    .procesoId(proceso.getId()).elemento(clave(clave)).xml(xml).build()));
            extensionXmlRepository.saveAll(nuevas);
        }

        private String clave(String clave) {
            if (clave.equals(organizacionEnElPlan)) {
                return organizacion;
            }
            if (clave.startsWith(organizacionEnElPlan + "#")) {
                return organizacion + clave.substring(organizacionEnElPlan.length());
            }
            return clave;
        }

        // ---------- Ayudas ----------

        private String id(PlanBpmn.Pool pool) {
            return idDelPool(pool.bpmnId());
        }

        private String idDelPool(String enElPlan) {
            return enElPlan.equals(organizacionEnElPlan) ? organizacion : enElPlan;
        }

        private static PlanBpmn.Clase clase(NodoFlujo nodo) {
            return switch (nodo) {
                case Actividad actividad -> PlanBpmn.Clase.ACTIVIDAD;
                case Gateway gateway -> PlanBpmn.Clase.GATEWAY;
                default -> PlanBpmn.Clase.EVENTO;
            };
        }

        private static boolean enElPlan(String id, Stream<String> delPlan) {
            return delPlan.anyMatch(id::equals);
        }

        private static int sobran(Stream<String> actuales, Stream<String> delPlan) {
            Set<String> plan = delPlan.collect(Collectors.toSet());
            return (int) actuales.filter(actual -> !plan.contains(actual)).count();
        }

        private static Set<String> ids(Stream<String> ids) {
            return ids.collect(Collectors.toSet());
        }

        private static <T> Map<String, T> porBpmnId(List<T> elementos, Function<T, String> bpmnId) {
            Map<String, T> mapa = new LinkedHashMap<>();
            elementos.forEach(elemento -> mapa.putIfAbsent(bpmnId.apply(elemento), elemento));
            return mapa;
        }
    }
}
