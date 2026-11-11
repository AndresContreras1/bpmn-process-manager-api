package com.facimus.procesos.modelado.bpmn;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.LongPredicate;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import com.facimus.procesos.common.xml.SecureXml;
import com.facimus.procesos.gestion.dto.response.ProcesoResponse;
import com.facimus.procesos.modelado.dto.response.ActividadResponse;
import com.facimus.procesos.modelado.dto.response.ArcoResponse;
import com.facimus.procesos.modelado.dto.response.CorrelacionResponse;
import com.facimus.procesos.modelado.dto.response.DiagramaResponse;
import com.facimus.procesos.modelado.dto.response.EventoResponse;
import com.facimus.procesos.modelado.dto.response.GatewayResponse;
import com.facimus.procesos.modelado.dto.response.LaneResponse;
import com.facimus.procesos.modelado.dto.response.MensajeResponse;
import com.facimus.procesos.modelado.dto.response.PoolResponse;
import com.facimus.procesos.modelado.model.AccionSiFalla;
import com.facimus.procesos.modelado.model.CampoDeMensaje;
import com.facimus.procesos.modelado.model.DisposicionBpmn.Arista;
import com.facimus.procesos.modelado.model.DisposicionBpmn.Forma;
import com.facimus.procesos.modelado.model.DisposicionBpmn.Geometria;
import com.facimus.procesos.modelado.model.DisposicionBpmn.Limites;
import com.facimus.procesos.modelado.model.DisposicionBpmn.Punto;
import com.facimus.procesos.modelado.model.ExtensionXml;
import com.facimus.procesos.modelado.model.Integracion;
import com.facimus.procesos.modelado.model.TipoActividad;
import com.facimus.procesos.modelado.model.TipoEvento;
import com.facimus.procesos.modelado.model.TipoGateway;
import com.facimus.procesos.modelado.model.TipoParticipante;

/**
 * PR 53: un diagrama y su geometria, en BPMN 2.0.2. Es una funcion pura: lo usan el borrador, la publicacion de una
 * version y la migracion que les dio su XML a las versiones que ya existian, y los tres tienen que escribir lo mismo.
 *
 * <p>La correspondencia:
 *
 * <ul>
 * <li>una {@code collaboration} con un {@code participant} por pool (la caja negra, sin proceso) y un
 * {@code messageFlow} por mensaje, cada uno con su {@code message};</li>
 * <li>un {@code process} por pool modelado, con sus lanes, sus nodos y sus flujos. Las tareas segun su tipo, los
 * gateways con su salida por defecto, los eventos con su definicion de mensaje y las condiciones como
 * {@code tFormalExpression} en el lenguaje de D6;</li>
 * <li>un {@code BPMNDiagram} con la geometria que el modelo ya tiene (PR 52);</li>
 * <li>lo que BPMN no tiene, en el espacio {@code bpm:} ({@link EspaciosBpmn#BPM}): tipo, integracion y orden del
 * participante, rol de la lane, orden de los flujos, y el contenido, los campos, el destino, el fallo, la respuesta y
 * la correlacion de cada mensaje.</li>
 * </ul>
 *
 * <p>PR 54: lo que un archivo importado traia de otra herramienta ({@link ExtensionesAjenas}) vuelve a su elemento:
 * sus atributos junto a los propios y sus hijos dentro del {@code extensionElements}, con los espacios de nombres
 * declarados una vez en la raiz.
 */
public final class EscritorBpmn {

    private static final String PARTICIPANT = "Participant";
    private static final String EXTENSION_ELEMENTS = "extensionElements";
    private static final String ORDEN = "orden";
    private static final String MESSAGE_REF = "messageRef";
    private static final String BPMNDI = "bpmndi";
    private static final String BPMN_ELEMENT = "bpmnElement";


    private static final Set<String> PREFIJOS_PROPIOS = Set.of("bpmn", BPMNDI, "dc", "di", "xsi", "bpm");

    private EscritorBpmn() {
    }

    public static byte[] escribir(DiagramaResponse diagrama, Geometria geometria) {
        return escribir(diagrama, geometria, Map.of());
    }

    /** @param extensiones lo ajeno de cada elemento, por su clave de {@link ExtensionXml} */
    public static byte[] escribir(DiagramaResponse diagrama, Geometria geometria, Map<String, String> extensiones) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        XMLStreamWriter xml = SecureXml.escritor(bytes);
        try {
            new Escritura(diagrama, geometria, new Salida(xml), new Ajenos(extensiones)).escribir();
            xml.close();
        } catch (XMLStreamException e) {
            throw new IllegalStateException("El BPMN del proceso " + diagrama.proceso().id() + " no se pudo escribir",
                    e);
        }
        return bytes.toByteArray();
    }

    /** Lo que se escribe dentro de un elemento abierto; puede fallar como todo lo de StAX. */
    @FunctionalInterface
    private interface Contenido {
        void escribir() throws XMLStreamException;
    }

    /** Lo ajeno guardado, ya leido, con el prefijo que toma cada espacio de nombres en este archivo. */
    private static final class Ajenos {

        private final Map<String, Element> envoltorios = new HashMap<>();
        private final Map<String, String> prefijos = new LinkedHashMap<>();

        Ajenos(Map<String, String> extensiones) {
            extensiones.forEach((clave, envoltorio) -> envoltorios.put(clave, ExtensionesAjenas.leer(envoltorio)));
            Set<String> tomados = new HashSet<>(PREFIJOS_PROPIOS);
            ExtensionesAjenas.espacios(envoltorios.values()).forEach((espacio, preferido) -> {
                String prefijo = preferido;
                int numero = 1;
                while (!prefijo.matches("[A-Za-z_][A-Za-z0-9_.-]*") || tomados.contains(prefijo)) {
                    prefijo = "ns" + numero++;
                }
                tomados.add(prefijo);
                prefijos.put(espacio, prefijo);
            });
        }

        void atributos(String clave, Salida salida) throws XMLStreamException {
            Element envoltorio = envoltorios.get(clave);
            if (envoltorio == null) {
                return;
            }
            for (Attr atributo : ExtensionesAjenas.atributos(envoltorio)) {
                salida.atributoAjeno(prefijos.get(atributo.getNamespaceURI()), atributo.getNamespaceURI(),
                        atributo.getLocalName(), atributo.getValue());
            }
        }

        List<Element> hijos(String clave) {
            Element envoltorio = envoltorios.get(clave);
            return envoltorio == null ? List.of() : Dom.hijos(envoltorio);
        }
    }

    /** Una exportacion: el diagrama, lo que se deriva de el y adonde se escribe. */
    private static final class Escritura {

        private final DiagramaResponse diagrama;
        private final Geometria geometria;
        private final Salida salida;
        private final Ajenos ajenos;
        private final String procesoId;
        private final Map<Long, String> pools = new HashMap<>();
        private final Map<Long, String> nodos = new HashMap<>();
        private final Map<Long, String> arcos = new HashMap<>();
        private final Map<Long, String> mensajes = new HashMap<>();
        private final Map<Long, Long> poolDeLaLane = new HashMap<>();

        Escritura(DiagramaResponse diagrama, Geometria geometria, Salida salida, Ajenos ajenos) {
            this.diagrama = diagrama;
            this.geometria = geometria;
            this.salida = salida;
            this.ajenos = ajenos;
            this.procesoId = String.valueOf(diagrama.proceso().id());
            diagrama.pools().forEach(pool -> pools.put(pool.id(), IdsBpmn.de(pool.bpmnId(), PARTICIPANT, pool.id())));
            diagrama.lanes().forEach(lane -> poolDeLaLane.put(lane.id(), lane.poolId()));
            diagrama.actividades().forEach(nodo -> nodos.put(nodo.id(), IdsBpmn.de(nodo.bpmnId(), "Activity", nodo.id())));
            diagrama.gateways().forEach(nodo -> nodos.put(nodo.id(), IdsBpmn.de(nodo.bpmnId(), "Gateway", nodo.id())));
            diagrama.eventos().forEach(nodo -> nodos.put(nodo.id(), IdsBpmn.de(nodo.bpmnId(), "Event", nodo.id())));
            diagrama.arcos().forEach(arco -> arcos.put(arco.id(), IdsBpmn.de(arco.bpmnId(), "Flow", arco.id())));
            diagrama.mensajes().forEach(mensaje -> mensajes.put(mensaje.id(),
                    IdsBpmn.de(mensaje.bpmnId(), "MessageFlow", mensaje.id())));
        }

        void escribir() throws XMLStreamException {
            ProcesoResponse proceso = diagrama.proceso();
            salida.empezar();
            salida.abrir("bpmn", "definitions");
            salida.declararEspacios(ajenos.prefijos);
            salida.atributo("id", "Definitions_" + procesoId);
            salida.atributo("name", proceso.nombre());
            salida.atributo("targetNamespace", EspaciosBpmn.PROCESOS);
            salida.atributo("expressionLanguage", EspaciosBpmn.CONDICIONES);
            salida.atributo("exporter", "BPMN Process Manager");
            ajenos.atributos(ExtensionXml.DEFINICIONES, salida);
            for (MensajeResponse mensaje : ordenados(diagrama.mensajes(), MensajeResponse::id)) {
                salida.vacio("bpmn", "message");
                salida.atributo("id", idDelMensaje(mensaje.id()));
                salida.atributo("name", mensaje.nombre());
            }
            colaboracion(proceso);
            for (PoolResponse pool : diagrama.pools()) {
                if (!pool.cajaNegra()) {
                    proceso(pool);
                }
            }
            dibujo();
            salida.cerrar();
            salida.terminar();
        }

        private void colaboracion(ProcesoResponse proceso) throws XMLStreamException {
            salida.abrir("bpmn", "collaboration");
            salida.atributo("id", "Collaboration_" + procesoId);
            salida.atributo("name", proceso.nombre());
            ajenos.atributos(ExtensionXml.COLABORACION, salida);
            documentacion(proceso.descripcion());
            extensiones(ExtensionXml.COLABORACION, proceso.categoria() != null, () -> {
                salida.vacio("bpm", "proceso");
                salida.atributo("categoria", proceso.categoria());
            });
            for (PoolResponse pool : diagrama.pools()) {
                String id = pools.get(pool.id());
                salida.abrir("bpmn", "participant");
                salida.atributo("id", id);
                salida.atributo("name", pool.nombre());
                salida.atributo("processRef", pool.cajaNegra() ? null : idDelProceso(pool.id()));
                ajenos.atributos(id, salida);
                extensiones(id, true, () -> {
                    salida.vacio("bpm", "participante");
                    salida.atributo("tipo", pool.tipoParticipante());
                    salida.atributo("integracion", pool.integracion() == Integracion.NINGUNA ? null
                            : pool.integracion());
                    salida.atributo(ORDEN, pool.orden());
                });
                salida.cerrar();
            }
            for (MensajeResponse mensaje : ordenados(diagrama.mensajes(), MensajeResponse::id)) {
                flujoDeMensaje(mensaje);
            }
            salida.cerrar();
        }

        private void flujoDeMensaje(MensajeResponse mensaje) throws XMLStreamException {
            String id = mensajes.get(mensaje.id());
            salida.abrir("bpmn", "messageFlow");
            salida.atributo("id", id);
            salida.atributo("name", mensaje.nombre());
            salida.atributo("sourceRef", extremo(mensaje.nodoOrigenId(), mensaje.poolOrigenId()));
            salida.atributo("targetRef", extremo(mensaje.nodoDestinoId(), mensaje.poolDestinoId()));
            salida.atributo(MESSAGE_REF, idDelMensaje(mensaje.id()));
            ajenos.atributos(id, salida);
            extensiones(id, true, () -> datosDelMensaje(mensaje));
            salida.cerrar();
        }

        private void datosDelMensaje(MensajeResponse mensaje) throws XMLStreamException {
            salida.abrir("bpm", "mensaje");
            salida.atributo("destino", mensaje.tipoDestino());
            salida.atributo("siFalla", mensaje.siFalla() == AccionSiFalla.CONTINUAR ? null : mensaje.siFalla());
            salida.atributo("manejoError", mensaje.nodoManejoErrorId() == null ? null
                    : nodos.get(mensaje.nodoManejoErrorId()));
            salida.atributo("origenExterno", mensaje.origenExterno() ? true : null);
            salida.atributo("variable", mensaje.variable());
            salida.atributo("respuesta", mensaje.respuestaEsperadaId() == null ? null
                    : mensajes.get(mensaje.respuestaEsperadaId()));
            salida.conTexto("bpm", "contenido", mensaje.contenido());
            if (mensaje.usoDeLosDatos() != null) {
                salida.conTexto("bpm", "usoDeLosDatos", mensaje.usoDeLosDatos());
            }
            for (CampoDeMensaje campo : mensaje.campos() == null ? List.<CampoDeMensaje>of() : mensaje.campos()) {
                salida.vacio("bpm", "campo");
                salida.atributo("nombre", campo.nombre());
                salida.atributo("tipo", campo.tipo());
            }
            Optional<CorrelacionResponse> correlacion = diagrama.correlaciones().stream()
                    .filter(una -> mensaje.id().equals(una.mensajeId())).findFirst();
            if (correlacion.isPresent()) {
                salida.vacio("bpm", "correlacion");
                salida.atributo("criterio", correlacion.get().criterio());
                salida.atributo("campo", correlacion.get().campo());
                salida.atributo("sinCaso", correlacion.get().sinCaso());
            }
            salida.cerrar();
        }

        private void proceso(PoolResponse pool) throws XMLStreamException {
            String clave = ExtensionXml.delProceso(pools.get(pool.id()));
            salida.abrir("bpmn", "process");
            salida.atributo("id", idDelProceso(pool.id()));
            salida.atributo("name", pool.nombre());
            salida.atributo("isExecutable", pool.tipoParticipante() == TipoParticipante.EMPRESA);
            ajenos.atributos(clave, salida);
            extensiones(clave, false, null);
            List<LaneResponse> lanes = diagrama.lanes().stream().filter(lane -> lane.poolId().equals(pool.id()))
                    .sorted(Comparator.comparingInt(LaneResponse::orden).thenComparing(LaneResponse::id)).toList();
            if (!lanes.isEmpty()) {
                salida.abrir("bpmn", "laneSet");
                salida.atributo("id", IdsBpmn.derivado(pools.get(pool.id()), PARTICIPANT, "LaneSet"));
                for (LaneResponse lane : lanes) {
                    lane(lane);
                }
                salida.cerrar();
            }
            LongPredicate enElPool = laneId -> pool.id().equals(poolDeLaLane.get(laneId));
            for (EventoResponse evento : ordenados(diagrama.eventos(), EventoResponse::id)) {
                if (enElPool.test(evento.laneId())) {
                    evento(evento);
                }
            }
            for (ActividadResponse actividad : ordenados(diagrama.actividades(), ActividadResponse::id)) {
                if (enElPool.test(actividad.laneId())) {
                    actividad(actividad);
                }
            }
            for (GatewayResponse gateway : ordenados(diagrama.gateways(), GatewayResponse::id)) {
                if (enElPool.test(gateway.laneId())) {
                    gateway(gateway);
                }
            }
            for (ArcoResponse arco : ordenados(diagrama.arcos(), ArcoResponse::id)) {
                if (pool.id().equals(arco.poolId())) {
                    flujo(arco);
                }
            }
            salida.cerrar();
        }

        private void lane(LaneResponse lane) throws XMLStreamException {
            String id = IdsBpmn.de(lane.bpmnId(), "Lane", lane.id());
            salida.abrir("bpmn", "lane");
            salida.atributo("id", id);
            salida.atributo("name", lane.nombre());
            ajenos.atributos(id, salida);
            extensiones(id, true, () -> {
                salida.vacio("bpm", "lane");
                // Toda lane tiene rol; si una instantanea vieja no lo trajera, el nombre de la lane es lo mas cercano
                salida.atributo("rol", lane.rolProcesoNombre() != null ? lane.rolProcesoNombre() : lane.nombre());
                salida.atributo(ORDEN, lane.orden());
            });
            for (Long nodo : nodosDeLaLane(lane.id())) {
                salida.conTexto("bpmn", "flowNodeRef", nodos.get(nodo));
            }
            salida.cerrar();
        }

        private void actividad(ActividadResponse actividad) throws XMLStreamException {
            TipoActividad tipo = actividad.tipoActividad();
            String elemento = tipo == null ? "task" : switch (tipo) {
                case USUARIO -> "userTask";
                case SERVICIO -> "serviceTask";
                case ENVIO -> "sendTask";
                case RECEPCION -> "receiveTask";
            };
            String id = nodos.get(actividad.id());
            salida.abrir("bpmn", elemento);
            salida.atributo("id", id);
            salida.atributo("name", actividad.nombre());
            salida.atributo("default", porDefecto(actividad.id()));
            if (tipo == TipoActividad.ENVIO) {
                salida.atributo(MESSAGE_REF, mensajeQueSaleDe(actividad.id()));
            } else if (tipo == TipoActividad.RECEPCION) {
                salida.atributo(MESSAGE_REF, mensajeQueLlegaA(actividad.id()));
            }
            ajenos.atributos(id, salida);
            documentacion(actividad.descripcion());
            extensiones(id, false, null);
            entradasYSalidas(actividad.id());
            salida.cerrar();
        }

        private void gateway(GatewayResponse gateway) throws XMLStreamException {
            String elemento = switch (gateway.tipoGateway()) {
                case EXCLUSIVO -> "exclusiveGateway";
                case PARALELO -> "parallelGateway";
                case INCLUSIVO -> "inclusiveGateway";
            };
            String id = nodos.get(gateway.id());
            salida.abrir("bpmn", elemento);
            salida.atributo("id", id);
            salida.atributo("name", gateway.nombre());
            // El paralelo no elige: BPMN no le permite una salida por defecto
            salida.atributo("default", gateway.tipoGateway() == TipoGateway.PARALELO ? null : porDefecto(gateway.id()));
            ajenos.atributos(id, salida);
            extensiones(id, false, null);
            entradasYSalidas(gateway.id());
            salida.cerrar();
        }

        private void evento(EventoResponse evento) throws XMLStreamException {
            TipoEvento tipo = evento.tipoEvento();
            String elemento = elementoDelEvento(tipo);
            String id = nodos.get(evento.id());
            salida.abrir("bpmn", elemento);
            salida.atributo("id", id);
            salida.atributo("name", evento.nombre());
            ajenos.atributos(id, salida);
            extensiones(id, false, null);
            entradasYSalidas(evento.id());
            if (tipo == TipoEvento.MENSAJE_INICIO || tipo == TipoEvento.MENSAJE_INTERMEDIO
                    || tipo == TipoEvento.MENSAJE_FIN) {
                salida.vacio("bpmn", "messageEventDefinition");
                salida.atributo("id", IdsBpmn.derivado(id, "Event", "MessageEventDefinition"));
                salida.atributo(MESSAGE_REF, tipo.puedeEnviar() ? mensajeQueSaleDe(evento.id())
                        : mensajeQueLlegaA(evento.id()));
            }
            salida.cerrar();
        }

        private void flujo(ArcoResponse arco) throws XMLStreamException {
            String id = arcos.get(arco.id());
            boolean conCondicion = arco.condicion() != null && !arco.condicion().isBlank();
            boolean conAjenos = !ajenos.hijos(id).isEmpty();
            if (conCondicion || conAjenos) {
                salida.abrir("bpmn", "sequenceFlow");
            } else {
                salida.vacio("bpmn", "sequenceFlow");
            }
            salida.atributo("id", id);
            salida.atributo("name", arco.etiqueta());
            salida.atributo("sourceRef", nodos.get(arco.origenId()));
            salida.atributo("targetRef", nodos.get(arco.destinoId()));
            salida.atributo("bpm", ORDEN, arco.orden() == 0 ? null : arco.orden());
            ajenos.atributos(id, salida);
            if (!conCondicion && !conAjenos) {
                return;
            }
            extensiones(id, false, null);
            if (conCondicion) {
                salida.abrir("bpmn", "conditionExpression");
                salida.atributo("xsi", "type", "bpmn:tFormalExpression");
                salida.atributo("language", EspaciosBpmn.CONDICIONES);
                // Texto pegado a la etiqueta: con sangria, la condicion llevaria espacios que no son suyos
                salida.texto(arco.condicion());
                salida.cerrar();
            }
            salida.cerrar();
        }

        private void dibujo() throws XMLStreamException {
            salida.abrir(BPMNDI, "BPMNDiagram");
            salida.atributo("id", "BPMNDiagram_" + procesoId);
            salida.abrir(BPMNDI, "BPMNPlane");
            salida.atributo("id", "BPMNPlane_" + procesoId);
            salida.atributo(BPMN_ELEMENT, "Collaboration_" + procesoId);
            List<String> exclusivos = diagrama.gateways().stream()
                    .filter(gateway -> gateway.tipoGateway() == TipoGateway.EXCLUSIVO)
                    .map(gateway -> nodos.get(gateway.id())).toList();
            List<Forma> formas = geometria.formas().stream()
                    .sorted(Comparator.comparingInt(Escritura::capa)).toList();
            for (Forma forma : formas) {
                salida.abrir(BPMNDI, "BPMNShape");
                salida.atributo("id", forma.elemento() + "_di");
                salida.atributo(BPMN_ELEMENT, forma.elemento());
                boolean horizontal = "PARTICIPANTE".equals(forma.tipo()) || "LANE".equals(forma.tipo());
                salida.atributo("isHorizontal", horizontal ? true : null);
                salida.atributo("isMarkerVisible", exclusivos.contains(forma.elemento()) ? true : null);
                ajenos.atributos(ExtensionXml.delDibujo(forma.elemento()), salida);
                limites("dc", forma.limites());
                etiqueta(forma.etiqueta());
                salida.cerrar();
            }
            for (Arista arista : geometria.aristas()) {
                salida.abrir(BPMNDI, "BPMNEdge");
                salida.atributo("id", arista.elemento() + "_di");
                salida.atributo(BPMN_ELEMENT, arista.elemento());
                ajenos.atributos(ExtensionXml.delDibujo(arista.elemento()), salida);
                for (Punto punto : arista.puntos()) {
                    salida.vacio("di", "waypoint");
                    salida.atributo("x", punto.x());
                    salida.atributo("y", punto.y());
                }
                etiqueta(arista.etiqueta());
                salida.cerrar();
            }
            salida.cerrar();
            salida.cerrar();
        }

        /**
         * El {@code extensionElements} del elemento: lo propio, si hay, y despues lo ajeno que trajo un archivo
         * importado. Sin ninguna de las dos cosas no se escribe.
         */
        private void extensiones(String clave, boolean conPropio, Contenido propio) throws XMLStreamException {
            List<Element> hijos = ajenos.hijos(clave);
            if (!conPropio && hijos.isEmpty()) {
                return;
            }
            salida.abrir("bpmn", EXTENSION_ELEMENTS);
            if (conPropio) {
                propio.escribir();
            }
            for (Element hijo : hijos) {
                salida.ajeno(hijo, ajenos.prefijos);
            }
            salida.cerrar();
        }

        private void etiqueta(Limites etiqueta) throws XMLStreamException {
            if (etiqueta != null) {
                salida.abrir(BPMNDI, "BPMNLabel");
                limites("dc", etiqueta);
                salida.cerrar();
            }
        }

        private void limites(String prefijo, Limites limites) throws XMLStreamException {
            salida.vacio(prefijo, "Bounds");
            salida.atributo("x", limites.x());
            salida.atributo("y", limites.y());
            salida.atributo("width", limites.ancho());
            salida.atributo("height", limites.alto());
        }

        private void documentacion(String texto) throws XMLStreamException {
            if (texto != null && !texto.isBlank()) {
                salida.conTexto("bpmn", "documentation", texto);
            }
        }

        private void entradasYSalidas(Long nodo) throws XMLStreamException {
            for (ArcoResponse arco : ordenados(diagrama.arcos(), ArcoResponse::id)) {
                if (nodo.equals(arco.destinoId())) {
                    salida.conTexto("bpmn", "incoming", arcos.get(arco.id()));
                }
            }
            for (ArcoResponse arco : ordenados(diagrama.arcos(), ArcoResponse::id)) {
                if (nodo.equals(arco.origenId())) {
                    salida.conTexto("bpmn", "outgoing", arcos.get(arco.id()));
                }
            }
        }

        private List<Long> nodosDeLaLane(Long laneId) {
            List<Long> deLaLane = new ArrayList<>();
            ordenados(diagrama.eventos(), EventoResponse::id).stream().filter(nodo -> laneId.equals(nodo.laneId()))
                    .forEach(nodo -> deLaLane.add(nodo.id()));
            ordenados(diagrama.actividades(), ActividadResponse::id).stream()
                    .filter(nodo -> laneId.equals(nodo.laneId())).forEach(nodo -> deLaLane.add(nodo.id()));
            ordenados(diagrama.gateways(), GatewayResponse::id).stream().filter(nodo -> laneId.equals(nodo.laneId()))
                    .forEach(nodo -> deLaLane.add(nodo.id()));
            return deLaLane;
        }

        private String porDefecto(Long nodo) {
            return diagrama.arcos().stream().filter(arco -> nodo.equals(arco.origenId()) && arco.porDefecto())
                    .min(Comparator.comparing(ArcoResponse::id)).map(arco -> arcos.get(arco.id())).orElse(null);
        }

        private String mensajeQueSaleDe(Long nodo) {
            return diagrama.mensajes().stream().filter(mensaje -> nodo.equals(mensaje.nodoOrigenId()))
                    .min(Comparator.comparing(MensajeResponse::id)).map(mensaje -> idDelMensaje(mensaje.id()))
                    .orElse(null);
        }

        private String mensajeQueLlegaA(Long nodo) {
            return diagrama.mensajes().stream().filter(mensaje -> nodo.equals(mensaje.nodoDestinoId()))
                    .min(Comparator.comparing(MensajeResponse::id)).map(mensaje -> idDelMensaje(mensaje.id()))
                    .orElse(null);
        }

        /** Un mensaje sale de un nodo, o del borde de su pool si no tiene. */
        private String extremo(Long nodo, Long pool) {
            return nodo != null ? nodos.get(nodo) : pools.get(pool);
        }

        private String idDelProceso(Long pool) {
            return IdsBpmn.derivado(pools.get(pool), PARTICIPANT, "Process");
        }

        private String idDelMensaje(Long mensaje) {
            return IdsBpmn.derivado(mensajes.get(mensaje), "MessageFlow", "Message");
        }

        /** Primero los pools, despues las lanes y al final lo de dentro: asi lo dibuja quien lea el archivo. */
        private static int capa(Forma forma) {
            return switch (forma.tipo()) {
                case "PARTICIPANTE" -> 0;
                case "LANE" -> 1;
                default -> 2;
            };
        }

        /** El elemento BPMN de un evento: de inicio, de fin o una espera. */
        private static String elementoDelEvento(TipoEvento tipo) {
            if (tipo.empiezaElProceso()) {
                return "startEvent";
            }
            return tipo.terminaElProceso() ? "endEvent" : "intermediateCatchEvent";
        }
    }


    private static <T> List<T> ordenados(List<T> elementos, Function<T, Long> id) {
        return elementos.stream().sorted(Comparator.comparing(id)).toList();
    }
}
