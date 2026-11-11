package com.facimus.procesos.modelado.bpmn;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.facimus.procesos.common.xml.LimitesXml;
import com.facimus.procesos.common.xml.SecureXml;
import com.facimus.procesos.gestion.model.ModoProceso;
import com.facimus.procesos.modelado.dto.response.DiagramaResponse;
import com.facimus.procesos.modelado.model.CampoDeMensaje;
import com.facimus.procesos.modelado.model.DireccionAsociacion;
import com.facimus.procesos.modelado.model.TipoActividad;
import com.facimus.procesos.modelado.model.TipoArtefacto;
import com.facimus.procesos.modelado.model.TipoAsociacion;
import com.facimus.procesos.modelado.model.TipoDeDato;
import com.facimus.procesos.modelado.model.TipoEvento;
import com.facimus.procesos.modelado.model.TipoGateway;
import com.facimus.procesos.modelado.model.TipoTemporizador;
import com.facimus.procesos.modelado.service.DiagramaArmado;

/** PR 53: lo que se exporta es BPMN 2.0.2 valido para el esquema de la OMG y el de las extensiones. */
class EscritorBpmnTest {

    private static final String BPMN = EspaciosBpmn.BPMN;
    private static final String BPM = EspaciosBpmn.BPM;

    @Test
    @DisplayName("La demo exportada valida contra los XSD de la OMG y el de bpm:")
    void laDemo_valida() {
        assertThat(EsquemaBpmn.problemas(exportar(DiagramaArmado.demo().diagrama()))).isEmpty();
    }

    @Test
    @DisplayName("Con cada tipo de tarea, gateway, evento y participante tambien valida, y cada uno sale como el suyo")
    void cadaTipo_valida() {
        Document documento = leer(exportar(conTodo().diagrama()));

        assertThat(EsquemaBpmn.problemas(exportar(conTodo().diagrama()))).isEmpty();
        assertThat(elementos(documento, BPMN, "userTask")).hasSize(2);
        assertThat(elementos(documento, BPMN, "serviceTask")).hasSize(1);
        assertThat(elementos(documento, BPMN, "sendTask")).hasSize(2);
        assertThat(elementos(documento, BPMN, "receiveTask")).hasSize(1);
        assertThat(elementos(documento, BPMN, "parallelGateway")).hasSize(1);
        assertThat(elementos(documento, BPMN, "inclusiveGateway")).hasSize(1);
        assertThat(elementos(documento, BPMN, "startEvent")).hasSize(1);
        assertThat(elementos(documento, BPMN, "intermediateCatchEvent")).hasSize(2);
        assertThat(elementos(documento, BPMN, "endEvent")).hasSize(3);
        assertThat(elementos(documento, BPMN, "messageEventDefinition")).hasSize(4);
    }

    @Test
    @DisplayName("Cada pool modelado es un proceso; solo el de la organizacion es ejecutable y la caja negra no tiene")
    void procesos() {
        Document documento = leer(exportar(conTodo().diagrama()));

        List<Element> procesos = elementos(documento, BPMN, "process");
        assertThat(procesos).extracting(proceso -> proceso.getAttribute("name"))
                .containsExactly("Demo Store", "Carrier");
        assertThat(procesos).extracting(proceso -> proceso.getAttribute("isExecutable"))
                .containsExactly("true", "false");
        Element cliente = participante(documento, "Customer");
        assertThat(cliente.hasAttribute("processRef")).isFalse();
        assertThat(participante(documento, "Carrier").getAttribute("processRef"))
                .isEqualTo(procesos.get(1).getAttribute("id"));
        Element extension = (Element) cliente.getElementsByTagNameNS(BPM, "participante").item(0);
        assertThat(extension.getAttribute("tipo")).isEqualTo("CLIENTE");
        assertThat(extension.getAttribute("integracion")).isEqualTo("CLIENTE");
    }

    @Test
    @DisplayName("Las condiciones van en el lenguaje de D6, la salida por defecto en el gateway y el orden en bpm:")
    void flujos() {
        Document documento = leer(exportar(conTodo().diagrama()));

        Element condicion = elementos(documento, BPMN, "conditionExpression").getFirst();
        assertThat(condicion.getTextContent()).isEqualTo("payment.status == APPROVED");
        assertThat(condicion.getAttribute("language")).isEqualTo(EspaciosBpmn.CONDICIONES);
        Element exclusivo = elementos(documento, BPMN, "exclusiveGateway").getFirst();
        Element porDefecto = flujo(documento, exclusivo.getAttribute("default"));
        assertThat(porDefecto.getAttribute("targetRef")).isEqualTo(id(documento, "Cancel order"));
        // El paralelo no elige: aunque el modelo tenga una salida marcada, BPMN no la acepta
        assertThat(elementos(documento, BPMN, "parallelGateway").getFirst().hasAttribute("default")).isFalse();
        // Solo el arco con orden lo lleva: los demas valen 0, que es lo que se entiende sin el atributo
        List<Element> conOrden = elementos(documento, BPMN, "sequenceFlow").stream()
                .filter(flujo -> flujo.hasAttributeNS(BPM, "orden")).toList();
        assertThat(conOrden).hasSize(1);
        assertThat(conOrden.getFirst().getAttributeNS(BPM, "orden")).isEqualTo("2");
        assertThat(conOrden.getFirst().getAttribute("targetRef")).isEqualTo(id(documento, "Order cancelled"));
        assertThat(elementos(documento, BPMN, "sequenceFlow")).extracting(flujo -> flujo.getAttribute("name"))
                .contains("Big order");
    }

    @Test
    @DisplayName("Un mensaje lleva su contenido, sus campos, su variable, su respuesta y su correlacion")
    void mensajes() {
        Document documento = leer(exportar(conTodo().diagrama()));

        Element pedido = elementos(documento, BPMN, "messageFlow").stream()
                .filter(flujo -> "Order placed".equals(flujo.getAttribute("name"))).findFirst().orElseThrow();
        assertThat(pedido.getAttribute("sourceRef")).isEqualTo(participante(documento, "Customer").getAttribute("id"));
        assertThat(pedido.getAttribute("targetRef")).isEqualTo(id(documento, "Order received"));
        Element mensaje = (Element) pedido.getElementsByTagNameNS(BPM, "mensaje").item(0);
        assertThat(mensaje.getAttribute("variable")).isEqualTo("order");
        assertThat(elementos(mensaje, BPM, "campo")).extracting(campo -> campo.getAttribute("nombre") + ":"
                + campo.getAttribute("tipo")).containsExactly("orderId:TEXTO", "total:NUMERO");
        assertThat(((Element) mensaje.getElementsByTagNameNS(BPM, "correlacion").item(0)).getAttribute("sinCaso"))
                .isEqualTo("INICIAR_CASO");
        Element pago = elementos(documento, BPMN, "messageFlow").stream()
                .filter(flujo -> "Payment authorization request".equals(flujo.getAttribute("name"))).findFirst()
                .orElseThrow();
        Element delPago = (Element) pago.getElementsByTagNameNS(BPM, "mensaje").item(0);
        assertThat(delPago.getAttribute("siFalla")).isEqualTo("MANEJAR_ERROR");
        assertThat(delPago.getAttribute("manejoError")).isEqualTo(id(documento, "Cancel order"));
        assertThat(flujoDeMensaje(documento, delPago.getAttribute("respuesta")).getAttribute("name"))
                .isEqualTo("Payment authorization result");
        // Cada flujo de mensaje apunta a su message, y la tarea que lo manda tambien
        Element enviar = elementos(documento, BPMN, "sendTask").stream()
                .filter(tarea -> "Request payment authorization".equals(tarea.getAttribute("name"))).findFirst()
                .orElseThrow();
        assertThat(enviar.getAttribute("messageRef")).isEqualTo(pago.getAttribute("messageRef"));
    }

    @Test
    @DisplayName("Un nombre con < & y comillas sale escapado y se lee igual")
    void nombresRaros() {
        DiagramaArmado armado = conTodo();
        armado.actividad(DiagramaArmado.VENTAS, "Check <price> & \"stock\"", TipoActividad.USUARIO);

        Document documento = leer(exportar(armado.diagrama()));

        assertThat(elementos(documento, BPMN, "userTask")).extracting(tarea -> tarea.getAttribute("name"))
                .contains("Check <price> & \"stock\"");
    }

    @Test
    @DisplayName("Exportar dos veces lo mismo da los mismos bytes: un archivo exportado se puede comparar en un diff")
    void determinista() {
        assertThat(exportar(conTodo().diagrama())).isEqualTo(exportar(conTodo().diagrama()));
    }

    @Test
    @DisplayName("El validador si ve un error: un tipo de participante que no existe no pasa")
    void elEsquemaVeLosErrores() {
        String xml = new String(exportar(DiagramaArmado.demo().diagrama()), StandardCharsets.UTF_8)
                .replace("tipo=\"CLIENTE\"", "tipo=\"MARCIANO\"");

        assertThat(EsquemaBpmn.problemas(xml.getBytes(StandardCharsets.UTF_8)))
                .anySatisfy(problema -> assertThat(problema).startsWith("Linea ").contains("MARCIANO"));
    }

    @Test
    @DisplayName("Lo descriptivo sale valido contra el esquema de la OMG")
    void descriptivo_valida() {
        assertThat(EsquemaBpmn.problemas(exportar(descriptivo().diagrama()))).isEmpty();
    }

    @Test
    @DisplayName("Un proceso documental sale con isExecutable en false, y uno ejecutable en true")
    void modo() {
        DiagramaArmado armado = descriptivo();
        assertThat(elementos(leer(exportar(armado.diagrama())), BPMN, "process").getFirst().getAttribute("isExecutable"))
                .isEqualTo("false");

        armado.modo(ModoProceso.EJECUTABLE);
        assertThat(elementos(leer(exportar(armado.diagrama())), BPMN, "process").getFirst().getAttribute("isExecutable"))
                .isEqualTo("true");
    }

    @Test
    @DisplayName("Los temporizadores dicen cuando saltan, el de borde va pegado y el fin termina")
    void eventos() {
        Document documento = leer(exportar(descriptivo().diagrama()));

        Element borde = elementos(documento, BPMN, "boundaryEvent").getFirst();
        assertThat(borde.getAttribute("cancelActivity")).isEmpty();
        assertThat(borde.getAttribute("attachedToRef")).startsWith("Activity_");
        assertThat(elementos(borde, BPMN, "timeDuration").getFirst().getTextContent()).isEqualTo("PT2H");
        assertThat(elementos(documento, BPMN, "timeCycle").getFirst().getTextContent()).isEqualTo("R3/PT1H");
        assertThat(elementos(documento, BPMN, "terminateEventDefinition")).hasSize(1);
        assertThat(elementos(documento, BPMN, "task")).hasSize(1);
    }

    @Test
    @DisplayName("Los datos llevan su dataObject, y lo que entra a una tarea llega a una propiedad suya")
    void datos() {
        Document documento = leer(exportar(descriptivo().diagrama()));

        Element referencia = elementos(documento, BPMN, "dataObjectReference").getFirst();
        assertThat(elementos(documento, BPMN, "dataObject")).extracting(objeto -> objeto.getAttribute("id"))
                .containsExactly(referencia.getAttribute("dataObjectRef"));
        assertThat(elementos(documento, BPMN, "dataStoreReference")).hasSize(1);
        Element entrada = elementos(documento, BPMN, "dataInputAssociation").getFirst();
        assertThat(elementos(entrada, BPMN, "sourceRef").getFirst().getTextContent())
                .isEqualTo(referencia.getAttribute("id"));
        assertThat(elementos(documento, BPMN, "property")).extracting(propiedad -> propiedad.getAttribute("id"))
                .containsExactly(elementos(entrada, BPMN, "targetRef").getFirst().getTextContent());
        assertThat(elementos(documento, BPMN, "dataOutputAssociation")).hasSize(1);
    }

    @Test
    @DisplayName("Las anotaciones y los grupos van en su proceso o sueltos, y una asociacion colgando no se escribe")
    void artefactos() {
        Document documento = leer(exportar(descriptivo().diagrama()));

        Element proceso = elementos(documento, BPMN, "process").getFirst();
        Element colaboracion = elementos(documento, BPMN, "collaboration").getFirst();
        assertThat(elementos(proceso, BPMN, "textAnnotation")).hasSize(1);
        assertThat(elementos(colaboracion, BPMN, "textAnnotation")).hasSize(1);
        Element grupo = elementos(colaboracion, BPMN, "group").getFirst();
        assertThat(elementos(documento, BPMN, "categoryValue")).singleElement().satisfies(valor -> {
            assertThat(valor.getAttribute("id")).isEqualTo(grupo.getAttribute("categoryValueRef"));
            assertThat(valor.getAttribute("value")).isEqualTo("Checks");
        });
        List<Element> asociaciones = elementos(documento, BPMN, "association");
        assertThat(asociaciones).singleElement()
                .satisfies(una -> assertThat(una.getAttribute("associationDirection")).isEqualTo("One"));
    }

    @Test
    @DisplayName("Un subproceso lleva dentro su flujo y sale expandido; la llamada nombra su proceso")
    void subprocesos() {
        DiagramaArmado armado = DiagramaArmado.reciennacido();
        armado.lane(DiagramaArmado.TIENDA, "Sales");
        armado.evento("Sales", "Start", TipoEvento.INICIO);
        Long subproceso = armado.actividadEn("Sales", "Inside", TipoActividad.SUBPROCESO, null, null);
        armado.eventoEn("Sales", "Inner start", TipoEvento.INICIO, "Inside");
        armado.actividadEn("Sales", "Inner work", TipoActividad.USUARIO, "Inside", null);
        armado.arco("Inner start", "Inner work");
        armado.actividadEn("Sales", "Black box", TipoActividad.SUBPROCESO_COLAPSADO, null, null);
        armado.actividadEn("Sales", "Call", TipoActividad.LLAMADA, null, 7L);
        armado.arco("Start", "Inside");
        armado.modo(ModoProceso.DOCUMENTAL);

        byte[] xml = exportar(armado.diagrama());
        Document documento = leer(xml);

        assertThat(EsquemaBpmn.problemas(xml)).isEmpty();
        Element contenedor = elementos(documento, BPMN, "subProcess").stream()
                .filter(uno -> uno.getAttribute("id").equals("Activity_" + subproceso)).findFirst().orElseThrow();
        assertThat(elementos(contenedor, BPMN, "userTask")).hasSize(1);
        assertThat(elementos(contenedor, BPMN, "sequenceFlow")).hasSize(1);
        assertThat(elementos(documento, BPMN, "callActivity").getFirst().getAttribute("calledElement"))
                .isEqualTo("Proceso_7");
        assertThat(elementos(documento, EspaciosBpmn.BPMNDI, "BPMNShape"))
                .filteredOn(forma -> "true".equals(forma.getAttribute("isExpanded")))
                .extracting(forma -> forma.getAttribute("bpmnElement")).containsExactly("Activity_" + subproceso);
        assertThat(elementos(documento, BPMN, "flowNodeRef")).extracting(Element::getTextContent)
                .doesNotContain("Activity_" + armado.id("Inner work"));
    }

    /** Un proceso documental con un caso de cada elemento descriptivo. */
    private static DiagramaArmado descriptivo() {
        DiagramaArmado armado = DiagramaArmado.reciennacido();
        armado.lane(DiagramaArmado.TIENDA, "Sales");
        armado.temporizador("Sales", "Every hour", TipoEvento.TEMPORIZADOR_INICIO, TipoTemporizador.CICLO, "R3/PT1H",
                null);
        Long tarea = armado.actividad("Sales", "Review", TipoActividad.SIN_TIPO);
        armado.temporizador("Sales", "Too late", TipoEvento.TEMPORIZADOR_BORDE, TipoTemporizador.DURACION, "PT2H",
                "Review");
        armado.evento("Sales", "Stop everything", TipoEvento.FIN_TERMINACION);
        armado.arco("Every hour", "Review");
        armado.arco("Review", "Stop everything");
        armado.arco("Too late", "Stop everything");
        String nota = armado.artefacto(DiagramaArmado.TIENDA, "Sales", "Nota", TipoArtefacto.ANOTACION,
                "Check by hand");
        armado.artefacto(null, null, "Suelta", TipoArtefacto.ANOTACION, "Loose");
        armado.artefacto(null, null, "Grupo", TipoArtefacto.GRUPO, "Checks");
        String objeto = armado.artefacto(DiagramaArmado.TIENDA, "Sales", "Objeto", TipoArtefacto.OBJETO_DATOS, "Order");
        String almacen = armado.artefacto(DiagramaArmado.TIENDA, "Sales", "Almacen", TipoArtefacto.ALMACEN_DATOS,
                "Stock");
        String actividad = "Activity_" + tarea;
        armado.asociacion(TipoAsociacion.ASOCIACION, nota, actividad, DireccionAsociacion.UNA);
        armado.asociacion(TipoAsociacion.ASOCIACION, nota, "Activity_9999", DireccionAsociacion.NINGUNA);
        armado.asociacion(TipoAsociacion.ENTRADA_DATOS, objeto, actividad, DireccionAsociacion.NINGUNA);
        armado.asociacion(TipoAsociacion.SALIDA_DATOS, actividad, almacen, DireccionAsociacion.NINGUNA);
        armado.modo(ModoProceso.DOCUMENTAL);
        return armado;
    }

    /** La demo con lo que le falta para tener un caso de cada tipo: recepcion, paralelo, inclusivo, fin de mensaje y un socio. */
    private static DiagramaArmado conTodo() {
        DiagramaArmado armado = DiagramaArmado.demo();
        armado.datosDelMensaje("Order placed", "order", new CampoDeMensaje("orderId", TipoDeDato.TEXTO),
                new CampoDeMensaje("total", TipoDeDato.NUMERO));
        armado.gateway(DiagramaArmado.VENTAS, "Split work", TipoGateway.PARALELO);
        armado.gateway(DiagramaArmado.VENTAS, "Any extras?", TipoGateway.INCLUSIVO);
        armado.arco("Receive order", "Split work");
        armado.arcoPorDefecto("Split work", "Any extras?");
        armado.arcoConEtiqueta("Any extras?", "Cancel order", "Big order", null);
        armado.arcoCon("Any extras?", "Order cancelled", "order.total > 100", 2);
        armado.dejaDeSerCajaNegra("Carrier");
        armado.lane("Carrier", "Dispatch");
        armado.actividad("Dispatch", "Take the shipment", TipoActividad.RECEPCION);
        armado.evento("Dispatch", "Shipment notified", TipoEvento.MENSAJE_FIN);
        return armado;
    }

    private static byte[] exportar(DiagramaResponse diagrama) {
        return EscritorBpmn.escribir(diagrama, GeometriaDelDiagrama.de(diagrama));
    }

    private static Document leer(byte[] xml) {
        return SecureXml.documento(new ByteArrayInputStream(xml), LimitesXml.BPMN);
    }

    private static Element participante(Document documento, String nombre) {
        return elementos(documento, BPMN, "participant").stream()
                .filter(participante -> nombre.equals(participante.getAttribute("name"))).findFirst().orElseThrow();
    }

    private static Element flujo(Document documento, String id) {
        return elementos(documento, BPMN, "sequenceFlow").stream()
                .filter(flujo -> id.equals(flujo.getAttribute("id"))).findFirst().orElseThrow();
    }

    private static Element flujoDeMensaje(Document documento, String id) {
        return elementos(documento, BPMN, "messageFlow").stream()
                .filter(flujo -> id.equals(flujo.getAttribute("id"))).findFirst().orElseThrow();
    }

    /** El id del nodo con ese nombre, sea del tipo que sea. */
    private static String id(Document documento, String nombre) {
        NodeList todos = documento.getElementsByTagNameNS(BPMN, "*");
        for (int i = 0; i < todos.getLength(); i++) {
            Element elemento = (Element) todos.item(i);
            if (nombre.equals(elemento.getAttribute("name")) && !"messageFlow".equals(elemento.getLocalName())) {
                return elemento.getAttribute("id");
            }
        }
        throw new AssertionError("No hay ningun nodo llamado " + nombre);
    }

    private static List<Element> elementos(Document documento, String espacio, String nombre) {
        return lista(documento.getElementsByTagNameNS(espacio, nombre));
    }

    private static List<Element> elementos(Element padre, String espacio, String nombre) {
        return lista(padre.getElementsByTagNameNS(espacio, nombre));
    }

    private static List<Element> lista(NodeList nodos) {
        List<Element> lista = new ArrayList<>();
        for (int i = 0; i < nodos.getLength(); i++) {
            lista.add((Element) nodos.item(i));
        }
        return lista;
    }
}
