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
import com.facimus.procesos.modelado.dto.response.DiagramaResponse;
import com.facimus.procesos.modelado.model.CampoDeMensaje;
import com.facimus.procesos.modelado.model.TipoActividad;
import com.facimus.procesos.modelado.model.TipoDeDato;
import com.facimus.procesos.modelado.model.TipoEvento;
import com.facimus.procesos.modelado.model.TipoGateway;
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

    /** La demo con lo que le falta para tener de todo: recepcion, paralelo, inclusivo, fin de mensaje y un socio. */
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
