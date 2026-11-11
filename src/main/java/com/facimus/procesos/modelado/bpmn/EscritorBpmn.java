package com.facimus.procesos.modelado.bpmn;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import com.facimus.procesos.common.xml.SecureXml;
import com.facimus.procesos.gestion.dto.response.ProcesoResponse;
import com.facimus.procesos.modelado.dto.response.ActividadResponse;
import com.facimus.procesos.modelado.dto.response.ArcoResponse;
import com.facimus.procesos.modelado.dto.response.ArtefactoResponse;
import com.facimus.procesos.modelado.dto.response.AsociacionResponse;
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
import com.facimus.procesos.modelado.model.TipoArtefacto;
import com.facimus.procesos.modelado.model.TipoAsociacion;
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
 * <p>PR 55: lo descriptivo. Los eventos de temporizador (de inicio, de espera y de borde) con su
 * {@code timerEventDefinition}, el fin de terminacion, la tarea sin tipo, las anotaciones y los grupos (en el proceso
 * de su pool, o en la colaboracion si van sueltos), los objetos de datos con su {@code dataObject} y los almacenes,
 * las asociaciones y las asociaciones de datos dentro de su actividad. Un proceso documental sale con
 * {@code isExecutable="false"}. Una asociacion con un extremo que ya no esta no se escribe: no tendria a donde ir.
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
    private static final String GROUP = "Group";
    private static final String CATEGORY_VALUE = "CategoryValue";
    private static final String DATA_ASSOCIATION = "DataAssociation";


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
        /** PR 55: el pool de cada cosa que una asociacion puede nombrar, y que cosas son datos. */
        private final Map<String, Long> poolDelElemento = new HashMap<>();
        private final Set<String> elementos = new HashSet<>();
        private final Set<String> datos = new HashSet<>();
        private final Set<Long> cajasNegras = new HashSet<>();
        /** PR 56: el subproceso de cada nodo que esta dentro de uno. */
        private final Map<Long, Long> padreDe = new HashMap<>();

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
            diagrama.pools().stream().filter(PoolResponse::cajaNegra).forEach(pool -> cajasNegras.add(pool.id()));
            diagrama.actividades().forEach(nodo -> anotarPadre(nodo.id(), nodo.padreId()));
            diagrama.gateways().forEach(nodo -> anotarPadre(nodo.id(), nodo.padreId()));
            diagrama.eventos().forEach(nodo -> anotarPadre(nodo.id(), nodo.padreId()));
            diagrama.actividades().forEach(nodo -> anotarElemento(nodos.get(nodo.id()), nodo.laneId()));
            diagrama.gateways().forEach(nodo -> anotarElemento(nodos.get(nodo.id()), nodo.laneId()));
            diagrama.eventos().forEach(nodo -> anotarElemento(nodos.get(nodo.id()), nodo.laneId()));
            diagrama.arcos().forEach(arco -> {
                elementos.add(arcos.get(arco.id()));
                poolDelElemento.put(arcos.get(arco.id()), arco.poolId());
            });
            for (ArtefactoResponse artefacto : diagrama.artefactos()) {
                elementos.add(artefacto.bpmnId());
                if (artefacto.poolId() != null && !cajasNegras.contains(artefacto.poolId())) {
                    poolDelElemento.put(artefacto.bpmnId(), artefacto.poolId());
                }
                if (esDato(artefacto)) {
                    datos.add(artefacto.bpmnId());
                }
            }
        }

        private void anotarPadre(Long nodo, Long padre) {
            if (padre != null) {
                padreDe.put(nodo, padre);
            }
        }

        private void anotarElemento(String id, Long laneId) {
            elementos.add(id);
            poolDelElemento.put(id, poolDeLaLane.get(laneId));
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
            categorias();
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
            artefactosDe(null);
            salida.cerrar();
        }

        /** PR 55: el nombre de un grupo es un valor de una categoria, que BPMN declara en la raiz. */
        private void categorias() throws XMLStreamException {
            for (ArtefactoResponse grupo : ordenados(diagrama.artefactos(), ArtefactoResponse::id)) {
                if (grupo.tipo() == TipoArtefacto.GRUPO && grupo.texto() != null) {
                    salida.abrir("bpmn", "category");
                    salida.atributo("id", IdsBpmn.derivado(grupo.bpmnId(), GROUP, "Category"));
                    salida.vacio("bpmn", "categoryValue");
                    salida.atributo("id", IdsBpmn.derivado(grupo.bpmnId(), GROUP, CATEGORY_VALUE));
                    salida.atributo("value", grupo.texto());
                    salida.cerrar();
                }
            }
        }

        /**
         * PR 55: las anotaciones, los grupos y las asociaciones de un proceso, o los sueltos de la colaboracion con
         * {@code pool} vacio. Van despues de los elementos del flujo, como pide BPMN.
         */
        private void artefactosDe(Long pool) throws XMLStreamException {
            for (ArtefactoResponse artefacto : ordenados(diagrama.artefactos(), ArtefactoResponse::id)) {
                boolean suyo = pool == null ? !poolDelElemento.containsKey(artefacto.bpmnId())
                        : pool.equals(poolDelElemento.get(artefacto.bpmnId()));
                if (!suyo) {
                    continue;
                }
                if (artefacto.tipo() == TipoArtefacto.ANOTACION) {
                    anotacion(artefacto);
                } else if (artefacto.tipo() == TipoArtefacto.GRUPO) {
                    grupo(artefacto);
                }
            }
            for (AsociacionResponse asociacion : ordenados(diagrama.asociaciones(), AsociacionResponse::id)) {
                if (asociacion.tipo() == TipoAsociacion.ASOCIACION && seEscribe(asociacion)
                        && Objects.equals(pool, poolDeLaAsociacion(asociacion))) {
                    asociacion(asociacion);
                }
            }
        }

        private void anotacion(ArtefactoResponse anotacion) throws XMLStreamException {
            String id = anotacion.bpmnId();
            salida.abrir("bpmn", "textAnnotation");
            salida.atributo("id", id);
            ajenos.atributos(id, salida);
            extensiones(id, false, null);
            if (anotacion.texto() != null) {
                salida.conTexto("bpmn", "text", anotacion.texto());
            }
            salida.cerrar();
        }

        private void grupo(ArtefactoResponse grupo) throws XMLStreamException {
            String id = grupo.bpmnId();
            boolean conAjenos = !ajenos.hijos(id).isEmpty();
            if (conAjenos) {
                salida.abrir("bpmn", "group");
            } else {
                salida.vacio("bpmn", "group");
            }
            salida.atributo("id", id);
            salida.atributo("categoryValueRef", grupo.texto() == null ? null
                    : IdsBpmn.derivado(id, GROUP, CATEGORY_VALUE));
            ajenos.atributos(id, salida);
            if (conAjenos) {
                extensiones(id, false, null);
                salida.cerrar();
            }
        }

        private void asociacion(AsociacionResponse asociacion) throws XMLStreamException {
            String id = asociacion.bpmnId();
            boolean conAjenos = !ajenos.hijos(id).isEmpty();
            if (conAjenos) {
                salida.abrir("bpmn", "association");
            } else {
                salida.vacio("bpmn", "association");
            }
            salida.atributo("id", id);
            salida.atributo("sourceRef", asociacion.origen());
            salida.atributo("targetRef", asociacion.destino());
            salida.atributo("associationDirection", switch (asociacion.direccion()) {
                case NINGUNA -> null;
                case UNA -> "One";
                case AMBAS -> "Both";
            });
            ajenos.atributos(id, salida);
            if (conAjenos) {
                extensiones(id, false, null);
                salida.cerrar();
            }
        }

        /** Los datos de un proceso: el objeto con su {@code dataObject}, y el almacen. */
        private void datosDelProceso(Long pool) throws XMLStreamException {
            for (ArtefactoResponse dato : ordenados(diagrama.artefactos(), ArtefactoResponse::id)) {
                if (!esDato(dato) || !pool.equals(poolDelElemento.get(dato.bpmnId()))) {
                    continue;
                }
                String id = dato.bpmnId();
                if (dato.tipo() == TipoArtefacto.OBJETO_DATOS) {
                    salida.vacio("bpmn", "dataObject");
                    salida.atributo("id", IdsBpmn.derivado(id, "DataObjectReference", "DataObject"));
                }
                boolean conAjenos = !ajenos.hijos(id).isEmpty();
                String elemento = dato.tipo() == TipoArtefacto.OBJETO_DATOS ? "dataObjectReference"
                        : "dataStoreReference";
                if (conAjenos) {
                    salida.abrir("bpmn", elemento);
                } else {
                    salida.vacio("bpmn", elemento);
                }
                salida.atributo("id", id);
                salida.atributo("name", dato.texto());
                salida.atributo("dataObjectRef", dato.tipo() == TipoArtefacto.OBJETO_DATOS
                        ? IdsBpmn.derivado(id, "DataObjectReference", "DataObject") : null);
                ajenos.atributos(id, salida);
                if (conAjenos) {
                    extensiones(id, false, null);
                    salida.cerrar();
                }
            }
        }

        /**
         * Los datos que entran a una actividad y los que salen de ella. BPMN pide que lo que entra llegue a algo de la
         * actividad: como bpmn.io, una propiedad que solo esta para eso.
         */
        private void datosDeLaActividad(String actividad) throws XMLStreamException {
            List<AsociacionResponse> entran = new ArrayList<>();
            List<AsociacionResponse> salen = new ArrayList<>();
            for (AsociacionResponse asociacion : ordenados(diagrama.asociaciones(), AsociacionResponse::id)) {
                if (asociacion.tipo() == TipoAsociacion.ENTRADA_DATOS && actividad.equals(asociacion.destino())
                        && datos.contains(asociacion.origen())) {
                    entran.add(asociacion);
                } else if (asociacion.tipo() == TipoAsociacion.SALIDA_DATOS && actividad.equals(asociacion.origen())
                        && datos.contains(asociacion.destino())) {
                    salen.add(asociacion);
                }
            }
            for (AsociacionResponse entrada : entran) {
                salida.vacio("bpmn", "property");
                salida.atributo("id", IdsBpmn.derivado(entrada.bpmnId(), DATA_ASSOCIATION, "Property"));
                salida.atributo("name", "__targetRef_placeholder");
            }
            for (AsociacionResponse entrada : entran) {
                salida.abrir("bpmn", "dataInputAssociation");
                salida.atributo("id", entrada.bpmnId());
                salida.conTexto("bpmn", "sourceRef", entrada.origen());
                salida.conTexto("bpmn", "targetRef", IdsBpmn.derivado(entrada.bpmnId(), DATA_ASSOCIATION,
                        "Property"));
                salida.cerrar();
            }
            for (AsociacionResponse saliente : salen) {
                salida.abrir("bpmn", "dataOutputAssociation");
                salida.atributo("id", saliente.bpmnId());
                salida.conTexto("bpmn", "targetRef", saliente.destino());
                salida.cerrar();
            }
        }

        /** Se escribe si sus dos extremos estan en el diagrama. */
        private boolean seEscribe(AsociacionResponse asociacion) {
            return elementos.contains(asociacion.origen()) && elementos.contains(asociacion.destino());
        }

        /**
         * El proceso donde va una asociacion: el de su origen, o el de su destino si el origen va suelto. Si los dos
         * estan en procesos distintos, o ninguno en uno, va en la colaboracion.
         */
        private Long poolDeLaAsociacion(AsociacionResponse asociacion) {
            Long desde = poolDelElemento.get(asociacion.origen());
            Long hasta = poolDelElemento.get(asociacion.destino());
            if (desde != null && hasta != null && !desde.equals(hasta)) {
                return null;
            }
            return desde != null ? desde : hasta;
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
            // PR 55: un proceso documental no se ejecuta, aunque sea el de la organizacion
            salida.atributo("isExecutable", pool.tipoParticipante() == TipoParticipante.EMPRESA
                    && diagrama.proceso().modo().ejecuta());
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
            nodosYFlujos(pool.id(), null);
            datosDelProceso(pool.id());
            artefactosDe(pool.id());
            salida.cerrar();
        }

        /** Lo que hay en un proceso o, con {@code padre}, dentro de un subproceso suyo. */
        private void nodosYFlujos(Long pool, Long padre) throws XMLStreamException {
            for (EventoResponse evento : ordenados(diagrama.eventos(), EventoResponse::id)) {
                if (pool.equals(poolDeLaLane.get(evento.laneId())) && Objects.equals(padre, evento.padreId())) {
                    evento(evento);
                }
            }
            for (ActividadResponse actividad : ordenados(diagrama.actividades(), ActividadResponse::id)) {
                if (pool.equals(poolDeLaLane.get(actividad.laneId())) && Objects.equals(padre, actividad.padreId())) {
                    actividad(actividad);
                }
            }
            for (GatewayResponse gateway : ordenados(diagrama.gateways(), GatewayResponse::id)) {
                if (pool.equals(poolDeLaLane.get(gateway.laneId())) && Objects.equals(padre, gateway.padreId())) {
                    gateway(gateway);
                }
            }
            for (ArcoResponse arco : ordenados(diagrama.arcos(), ArcoResponse::id)) {
                if (pool.equals(arco.poolId()) && Objects.equals(padre, padreDe.get(arco.origenId()))) {
                    flujo(arco);
                }
            }
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
                case SIN_TIPO -> "task";
                case SUBPROCESO, SUBPROCESO_COLAPSADO -> "subProcess";
                case LLAMADA -> "callActivity";
            };
            String id = nodos.get(actividad.id());
            salida.abrir("bpmn", elemento);
            salida.atributo("id", id);
            salida.atributo("name", actividad.nombre());
            salida.atributo("default", porDefecto(actividad.id()));
            salida.atributo("calledElement", actividad.procesoLlamadoId() == null ? null
                    : EspaciosBpmn.PREFIJO_PROCESO_LLAMADO + actividad.procesoLlamadoId());
            if (tipo == TipoActividad.ENVIO) {
                salida.atributo(MESSAGE_REF, mensajeQueSaleDe(actividad.id()));
            } else if (tipo == TipoActividad.RECEPCION) {
                salida.atributo(MESSAGE_REF, mensajeQueLlegaA(actividad.id()));
            }
            ajenos.atributos(id, salida);
            documentacion(actividad.descripcion());
            extensiones(id, false, null);
            entradasYSalidas(actividad.id());
            datosDeLaActividad(id);
            if (tipo != null && tipo.esSubproceso()) {
                nodosYFlujos(poolDeLaLane.get(actividad.laneId()), actividad.id());
            }
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
            String actividad = evento.adjuntoAId() == null ? null : nodos.get(evento.adjuntoAId());
            // Un evento de borde sin su actividad no tiene donde ir: queda como una espera
            String elemento = tipo.esDeBorde() && actividad == null ? "intermediateCatchEvent"
                    : elementoDelEvento(tipo);
            String id = nodos.get(evento.id());
            salida.abrir("bpmn", elemento);
            salida.atributo("id", id);
            salida.atributo("name", evento.nombre());
            if (tipo.esDeBorde() && actividad != null) {
                salida.atributo("cancelActivity", Boolean.FALSE.equals(evento.interrumpe()) ? false : null);
                salida.atributo("attachedToRef", actividad);
            }
            ajenos.atributos(id, salida);
            extensiones(id, false, null);
            entradasYSalidas(evento.id());
            if (tipo == TipoEvento.MENSAJE_INICIO || tipo == TipoEvento.MENSAJE_INTERMEDIO
                    || tipo == TipoEvento.MENSAJE_FIN) {
                salida.vacio("bpmn", "messageEventDefinition");
                salida.atributo("id", IdsBpmn.derivado(id, "Event", "MessageEventDefinition"));
                salida.atributo(MESSAGE_REF, tipo.puedeEnviar() ? mensajeQueSaleDe(evento.id())
                        : mensajeQueLlegaA(evento.id()));
            } else if (tipo.esTemporizador()) {
                temporizador(id, evento);
            } else if (tipo == TipoEvento.FIN_TERMINACION) {
                salida.vacio("bpmn", "terminateEventDefinition");
                salida.atributo("id", IdsBpmn.derivado(id, "Event", "TerminateEventDefinition"));
            }
            salida.cerrar();
        }

        /** PR 55: cuando salta, como una expresion formal de BPMN: una duracion, una fecha o un ciclo. */
        private void temporizador(String id, EventoResponse evento) throws XMLStreamException {
            String definicion = IdsBpmn.derivado(id, "Event", "TimerEventDefinition");
            if (evento.temporizador() == null || evento.tipoTemporizador() == null) {
                salida.vacio("bpmn", "timerEventDefinition");
                salida.atributo("id", definicion);
                return;
            }
            salida.abrir("bpmn", "timerEventDefinition");
            salida.atributo("id", definicion);
            salida.abrir("bpmn", switch (evento.tipoTemporizador()) {
                case DURACION -> "timeDuration";
                case FECHA -> "timeDate";
                case CICLO -> "timeCycle";
            });
            salida.atributo("xsi", "type", "bpmn:tFormalExpression");
            salida.texto(evento.temporizador());
            salida.cerrar();
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
            List<String> expandidos = diagrama.actividades().stream()
                    .filter(actividad -> actividad.tipoActividad() == TipoActividad.SUBPROCESO)
                    .map(actividad -> nodos.get(actividad.id())).toList();
            List<Forma> formas = geometria.formas().stream()
                    .sorted(Comparator.comparingInt(Escritura::capa)).toList();
            for (Forma forma : formas) {
                salida.abrir(BPMNDI, "BPMNShape");
                salida.atributo("id", forma.elemento() + "_di");
                salida.atributo(BPMN_ELEMENT, forma.elemento());
                boolean horizontal = "PARTICIPANTE".equals(forma.tipo()) || "LANE".equals(forma.tipo());
                salida.atributo("isHorizontal", horizontal ? true : null);
                salida.atributo("isMarkerVisible", exclusivos.contains(forma.elemento()) ? true : null);
                salida.atributo("isExpanded", expandidos.contains(forma.elemento()) ? true : null);
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
            // PR 56: lo de dentro de un subproceso va en el subproceso, que es el que esta en la lane
            ordenados(diagrama.eventos(), EventoResponse::id).stream()
                    .filter(nodo -> laneId.equals(nodo.laneId()) && nodo.padreId() == null)
                    .forEach(nodo -> deLaLane.add(nodo.id()));
            ordenados(diagrama.actividades(), ActividadResponse::id).stream()
                    .filter(nodo -> laneId.equals(nodo.laneId()) && nodo.padreId() == null)
                    .forEach(nodo -> deLaLane.add(nodo.id()));
            ordenados(diagrama.gateways(), GatewayResponse::id).stream()
                    .filter(nodo -> laneId.equals(nodo.laneId()) && nodo.padreId() == null)
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

        /** El elemento BPMN de un evento: de inicio, de fin, de borde o una espera. */
        private static String elementoDelEvento(TipoEvento tipo) {
            if (tipo.empiezaElProceso()) {
                return "startEvent";
            }
            if (tipo.esDeBorde()) {
                return "boundaryEvent";
            }
            return tipo.terminaElProceso() ? "endEvent" : "intermediateCatchEvent";
        }

        private static boolean esDato(ArtefactoResponse artefacto) {
            return artefacto.tipo() == TipoArtefacto.OBJETO_DATOS || artefacto.tipo() == TipoArtefacto.ALMACEN_DATOS;
        }
    }


    private static <T> List<T> ordenados(List<T> elementos, Function<T, Long> id) {
        return elementos.stream().sorted(Comparator.comparing(id)).toList();
    }
}
