package com.facimus.procesos.modelado.bpmn;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.facimus.procesos.common.xml.LimitesXml;
import com.facimus.procesos.common.xml.SecureXml;
import com.facimus.procesos.common.xml.XmlRechazadoException;
import com.facimus.procesos.modelado.bpmn.ModeloLeido.Ajeno;
import com.facimus.procesos.modelado.bpmn.ModeloLeido.Correlacion;
import com.facimus.procesos.modelado.bpmn.ModeloLeido.DatosDelMensaje;
import com.facimus.procesos.modelado.bpmn.ModeloLeido.Flujo;
import com.facimus.procesos.modelado.bpmn.ModeloLeido.FlujoDeMensaje;
import com.facimus.procesos.modelado.bpmn.ModeloLeido.Lane;
import com.facimus.procesos.modelado.bpmn.ModeloLeido.Nodo;
import com.facimus.procesos.modelado.bpmn.ModeloLeido.Participante;
import com.facimus.procesos.modelado.bpmn.ModeloLeido.Proceso;
import com.facimus.procesos.modelado.model.CampoDeMensaje;
import com.facimus.procesos.modelado.model.DisposicionBpmn.Limites;
import com.facimus.procesos.modelado.model.ExtensionXml;
import com.facimus.procesos.modelado.model.TipoDeDato;

/**
 * PR 54: un archivo BPMN 2.0, leido con {@link SecureXml} y sus limites para BPMN, a un {@link ModeloLeido}. No
 * decide nada: lo que el modelo no tiene queda anotado como no soportado, y lo que se convierte lo decide
 * {@link ConversionBpmn}. Lee lo que escriben Camunda Modeler, bpmn.io, Signavio o Bizagi: el espacio de BPMN puede
 * venir con cualquier prefijo, o sin ninguno.
 */
public final class LectorBpmn {

    private static final String BPMN = EspaciosBpmn.BPMN;
    private static final String BPM = EspaciosBpmn.BPM;

    /** Los nodos que el modelo tiene, o a los que se convierten. */
    static final Set<String> TAREAS = Set.of("task", "userTask", "serviceTask", "sendTask", "receiveTask",
            "manualTask", "businessRuleTask", "scriptTask");
    static final Set<String> GATEWAYS = Set.of("exclusiveGateway", "parallelGateway", "inclusiveGateway");
    static final Set<String> EVENTOS = Set.of("startEvent", "endEvent", "intermediateCatchEvent");

    /** Lo que un proceso trae y no es un elemento del diagrama: se lee aparte o no dice nada del dibujo. */
    private static final Set<String> SIN_DIBUJO = Set.of("documentation", "extensionElements", "laneSet",
            "sequenceFlow", "auditing", "monitoring");

    private LectorBpmn() {
    }

    public static ModeloLeido leer(byte[] xml) {
        Document documento = SecureXml.documento(new ByteArrayInputStream(xml), LimitesXml.BPMN);
        Element raiz = documento.getDocumentElement();
        if (!BPMN.equals(raiz.getNamespaceURI()) || !"definitions".equals(raiz.getLocalName())) {
            throw new XmlRechazadoException("El archivo no es BPMN 2.0: su raiz es " + raiz.getTagName()
                    + " y no bpmn:definitions.");
        }
        Lectura lectura = new Lectura();
        lectura.extension(ExtensionXml.DEFINICIONES, raiz);
        Element colaboracion = Dom.hijo(raiz, BPMN, "collaboration");
        String nombre = Dom.nombre(Dom.atributo(raiz, "name"));
        String descripcion = null;
        String categoria = null;
        if (colaboracion != null) {
            nombre = nombre != null ? nombre : Dom.nombre(Dom.atributo(colaboracion, "name"));
            descripcion = Dom.texto(Dom.hijo(colaboracion, BPMN, "documentation"));
            categoria = bpm(colaboracion, "proceso", "categoria");
            lectura.extension(ExtensionXml.COLABORACION, colaboracion);
            lectura.colaboracion(colaboracion);
        }
        for (Element mensaje : Dom.hijos(raiz, BPMN, "message")) {
            String id = Dom.atributo(mensaje, "id");
            if (id != null) {
                lectura.mensajes.put(id, Dom.nombre(Dom.atributo(mensaje, "name")));
            }
        }
        for (Element proceso : Dom.hijos(raiz, BPMN, "process")) {
            lectura.proceso(proceso);
        }
        for (Element diagrama : Dom.hijos(raiz, EspaciosBpmn.BPMNDI, "BPMNDiagram")) {
            for (Element plano : Dom.hijos(diagrama, EspaciosBpmn.BPMNDI, "BPMNPlane")) {
                lectura.dibujo(plano);
            }
        }
        return new ModeloLeido(nombre, descripcion, categoria, lectura.participantes, lectura.procesos,
                lectura.flujosDeMensaje, lectura.mensajes, lectura.formas, lectura.verticales, lectura.noSoportados,
                lectura.extensiones);
    }

    /** Lo que se va juntando mientras se recorre el archivo. */
    private static final class Lectura {

        private final List<Participante> participantes = new ArrayList<>();
        private final List<Proceso> procesos = new ArrayList<>();
        private final List<FlujoDeMensaje> flujosDeMensaje = new ArrayList<>();
        private final Map<String, String> mensajes = new LinkedHashMap<>();
        private final Map<String, Limites> formas = new LinkedHashMap<>();
        private final Set<String> verticales = new HashSet<>();
        private final List<Ajeno> noSoportados = new ArrayList<>();
        private final Map<String, String> extensiones = new LinkedHashMap<>();

        void colaboracion(Element colaboracion) {
            for (Element hijo : Dom.hijos(colaboracion)) {
                String id = Dom.atributo(hijo, "id");
                if (!BPMN.equals(hijo.getNamespaceURI())) {
                    continue;
                }
                switch (hijo.getLocalName()) {
                    case "participant" -> {
                        participantes.add(new Participante(id, Dom.nombre(Dom.atributo(hijo, "name")),
                                Dom.referencia(Dom.atributo(hijo, "processRef")), bpm(hijo, "participante", "tipo"),
                                bpm(hijo, "participante", "integracion"), entero(bpm(hijo, "participante", "orden"))));
                        extension(id, hijo);
                    }
                    case "messageFlow" -> {
                        flujosDeMensaje.add(new FlujoDeMensaje(id, Dom.nombre(Dom.atributo(hijo, "name")),
                                Dom.referencia(Dom.atributo(hijo, "sourceRef")),
                                Dom.referencia(Dom.atributo(hijo, "targetRef")),
                                Dom.referencia(Dom.atributo(hijo, "messageRef")), datosDelMensaje(hijo)));
                        extension(id, hijo);
                    }
                    case "documentation", "extensionElements" -> {
                        // Ya se leyeron con la colaboracion
                    }
                    default -> noSoportados.add(new Ajeno(id, hijo.getLocalName(),
                            Dom.nombre(Dom.atributo(hijo, "name")), "la colaboracion"));
                }
            }
        }

        void proceso(Element proceso) {
            String id = Dom.atributo(proceso, "id");
            String nombre = Dom.nombre(Dom.atributo(proceso, "name"));
            String ejecutable = Dom.atributo(proceso, "isExecutable");
            List<Lane> lanes = new ArrayList<>();
            boolean[] anidadas = {false};
            for (Element conjunto : Dom.hijos(proceso, BPMN, "laneSet")) {
                lanes(conjunto, lanes, anidadas);
            }
            List<Nodo> nodos = new ArrayList<>();
            List<Flujo> flujos = new ArrayList<>();
            for (Element hijo : Dom.hijos(proceso)) {
                if (!BPMN.equals(hijo.getNamespaceURI())) {
                    continue;
                }
                String elemento = hijo.getLocalName();
                String suId = Dom.atributo(hijo, "id");
                if (TAREAS.contains(elemento) || GATEWAYS.contains(elemento) || EVENTOS.contains(elemento)) {
                    nodos.add(new Nodo(suId, elemento, Dom.nombre(Dom.atributo(hijo, "name")),
                            Dom.texto(Dom.hijo(hijo, BPMN, "documentation")),
                            Dom.referencia(Dom.atributo(hijo, "default")), definiciones(hijo)));
                    extension(suId, hijo);
                } else if ("sequenceFlow".equals(elemento)) {
                    Element condicion = Dom.hijo(hijo, BPMN, "conditionExpression");
                    flujos.add(new Flujo(suId, Dom.nombre(Dom.atributo(hijo, "name")),
                            Dom.referencia(Dom.atributo(hijo, "sourceRef")),
                            Dom.referencia(Dom.atributo(hijo, "targetRef")), Dom.texto(condicion),
                            condicion == null ? null : Dom.atributo(condicion, "language"),
                            entero(Dom.atributo(hijo, BPM, "orden"))));
                    extension(suId, hijo);
                } else if (!SIN_DIBUJO.contains(elemento)) {
                    noSoportados.add(new Ajeno(suId, elemento, Dom.nombre(Dom.atributo(hijo, "name")),
                            nombre != null ? nombre : id));
                }
            }
            procesos.add(new Proceso(id, nombre, ejecutable == null ? null : Boolean.valueOf(ejecutable), lanes,
                    nodos, flujos, anidadas[0]));
            extension(claveDelProceso(id), proceso);
        }

        /** Las lanes hoja: una lane con lanes dentro se reparte entre ellas. */
        private void lanes(Element conjunto, List<Lane> lanes, boolean[] anidadas) {
            for (Element lane : Dom.hijos(conjunto, BPMN, "lane")) {
                Element hijas = Dom.hijo(lane, BPMN, "childLaneSet");
                if (hijas != null && !Dom.hijos(hijas, BPMN, "lane").isEmpty()) {
                    anidadas[0] = true;
                    lanes(hijas, lanes, anidadas);
                    continue;
                }
                List<String> nodos = Dom.hijos(lane, BPMN, "flowNodeRef").stream()
                        .map(referencia -> Dom.referencia(Dom.texto(referencia))).toList();
                String id = Dom.atributo(lane, "id");
                lanes.add(new Lane(id, Dom.nombre(Dom.atributo(lane, "name")), bpm(lane, "lane", "rol"),
                        entero(bpm(lane, "lane", "orden")), nodos));
                extension(id, lane);
            }
        }

        void dibujo(Element plano) {
            for (Element forma : Dom.hijos(plano, EspaciosBpmn.BPMNDI, "BPMNShape")) {
                String elemento = Dom.referencia(Dom.atributo(forma, "bpmnElement"));
                Element limites = Dom.hijo(forma, EspaciosBpmn.DC, "Bounds");
                if (elemento != null && limites != null) {
                    formas.putIfAbsent(elemento, new Limites(numero(limites, "x"), numero(limites, "y"),
                            numero(limites, "width"), numero(limites, "height")));
                    if ("false".equals(Dom.atributo(forma, "isHorizontal"))) {
                        verticales.add(elemento);
                    }
                    extension(ExtensionXml.delDibujo(elemento), forma);
                }
            }
            for (Element arista : Dom.hijos(plano, EspaciosBpmn.BPMNDI, "BPMNEdge")) {
                String elemento = Dom.referencia(Dom.atributo(arista, "bpmnElement"));
                if (elemento != null) {
                    extension(ExtensionXml.delDibujo(elemento), arista);
                }
            }
        }

        /** Lo ajeno del elemento, si trae algo: la primera vez que aparece la clave. */
        void extension(String clave, Element elemento) {
            if (clave == null || extensiones.containsKey(clave)) {
                return;
            }
            String envoltorio = ExtensionesAjenas.de(elemento);
            if (envoltorio != null) {
                extensiones.put(clave, envoltorio);
            }
        }
    }

    /** Lo ajeno de un proceso del archivo, hasta que la conversion sepa de que pool es. */
    static String claveDelProceso(String proceso) {
        return "process:" + proceso;
    }

    private static List<String> definiciones(Element evento) {
        return Dom.hijos(evento).stream()
                .filter(hijo -> BPMN.equals(hijo.getNamespaceURI()) && hijo.getLocalName().endsWith("EventDefinition"))
                .map(Element::getLocalName)
                .toList();
    }

    private static DatosDelMensaje datosDelMensaje(Element flujo) {
        Element mensaje = bpmElemento(flujo, "mensaje");
        if (mensaje == null) {
            return null;
        }
        List<CampoDeMensaje> campos = new ArrayList<>();
        for (Element campo : Dom.hijos(mensaje, BPM, "campo")) {
            String nombre = Dom.atributo(campo, "nombre");
            TipoDeDato tipo = tipoDeDato(Dom.atributo(campo, "tipo"));
            if (nombre != null && tipo != null) {
                campos.add(new CampoDeMensaje(nombre.strip(), tipo));
            }
        }
        Element correlacion = Dom.hijo(mensaje, BPM, "correlacion");
        return new DatosDelMensaje(Dom.texto(Dom.hijo(mensaje, BPM, "contenido")),
                Dom.texto(Dom.hijo(mensaje, BPM, "usoDeLosDatos")), campos, Dom.atributo(mensaje, "destino"),
                Dom.atributo(mensaje, "siFalla"), Dom.atributo(mensaje, "manejoError"),
                "true".equals(Dom.atributo(mensaje, "origenExterno")), Dom.atributo(mensaje, "variable"),
                Dom.atributo(mensaje, "respuesta"),
                correlacion == null ? null : new Correlacion(Dom.atributo(correlacion, "criterio"),
                        Dom.atributo(correlacion, "campo"), Dom.atributo(correlacion, "sinCaso")));
    }

    /** Un atributo de una extension {@code bpm:} del elemento, como {@code bpm:participante/@tipo}. */
    private static String bpm(Element elemento, String extension, String atributo) {
        Element encontrada = bpmElemento(elemento, extension);
        return encontrada == null ? null : Dom.atributo(encontrada, atributo);
    }

    private static Element bpmElemento(Element elemento, String extension) {
        Element extensiones = Dom.hijo(elemento, BPMN, "extensionElements");
        return extensiones == null ? null : Dom.hijo(extensiones, BPM, extension);
    }

    private static TipoDeDato tipoDeDato(String valor) {
        try {
            return valor == null ? null : TipoDeDato.valueOf(valor.strip());
        } catch (IllegalArgumentException desconocido) {
            return null;
        }
    }

    private static Integer entero(String valor) {
        try {
            return valor == null ? null : Integer.valueOf(valor.strip());
        } catch (NumberFormatException noEsUnNumero) {
            return null;
        }
    }

    private static double numero(Element elemento, String atributo) {
        try {
            String valor = Dom.atributo(elemento, atributo);
            return valor == null ? 0 : Double.parseDouble(valor);
        } catch (NumberFormatException noEsUnNumero) {
            return 0;
        }
    }
}
