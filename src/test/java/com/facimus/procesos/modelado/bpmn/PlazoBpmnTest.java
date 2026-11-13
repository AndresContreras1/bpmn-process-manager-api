package com.facimus.procesos.modelado.bpmn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

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
import com.facimus.procesos.modelado.bpmn.InformeBpmn.Hallazgo;
import com.facimus.procesos.modelado.dto.response.DiagramaResponse;
import com.facimus.procesos.modelado.dto.response.EscalamientoResponse;
import com.facimus.procesos.modelado.dto.response.PlazoResponse;
import com.facimus.procesos.modelado.model.AccionDeEscalamiento;
import com.facimus.procesos.modelado.model.TipoEvento;
import com.facimus.procesos.modelado.model.TipoTemporizador;
import com.facimus.procesos.modelado.model.UnidadDePlazo;
import com.facimus.procesos.modelado.service.DiagramaArmado;

/**
 * PR 71: el plazo de una tarea y del caso salen como bpm:plazo, y lo que escala un borde como bpm:escalamiento;
 * validan contra el XSD y vuelven enteros al importarlos.
 */
class PlazoBpmnTest {

    private static final String BPM = EspaciosBpmn.BPM;
    private static final String CABECERA = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<bpmn:definitions xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\" "
            + "xmlns:bpm=\"urn:bpmn-process-manager:bpm:1\" id=\"D\" targetNamespace=\"urn:prueba\">";
    private static final PlazoResponse OCHO_HORAS = new PlazoResponse(8, UnidadDePlazo.HORAS);
    private static final PlazoResponse CINCO_DIAS = new PlazoResponse(5, UnidadDePlazo.DIAS_HABILES);
    private static final EscalamientoResponse A_ANA = new EscalamientoResponse(AccionDeEscalamiento.REASIGNAR, 5L);

    @Test
    @DisplayName("Los plazos y el escalamiento validan contra los XSD de la OMG y el de bpm:")
    void valida() {
        assertThat(EsquemaBpmn.problemas(exportar(conPlazos().diagrama()))).isEmpty();
    }

    @Test
    @DisplayName("Cada uno sale en su nodo: el plazo en la userTask y en el startEvent, el escalamiento en el borde")
    void loQueSale() {
        Document documento = leer(exportar(conPlazos().diagrama()));

        List<Element> plazos = elementos(documento, "plazo");
        assertThat(plazos).extracting(plazo -> ((Element) plazo.getParentNode().getParentNode()).getLocalName(),
                plazo -> plazo.getAttribute("cantidad"), plazo -> plazo.getAttribute("unidad"))
                .containsExactlyInAnyOrder(tuple("userTask", "8", "HORAS"), tuple("startEvent", "5", "DIAS_HABILES"));
        List<Element> escalamientos = elementos(documento, "escalamiento");
        assertThat(escalamientos).singleElement().satisfies(escalamiento -> {
            assertThat(((Element) escalamiento.getParentNode().getParentNode()).getLocalName())
                    .isEqualTo("boundaryEvent");
            assertThat(escalamiento.getAttribute("accion")).isEqualTo("REASIGNAR");
            assertThat(escalamiento.getAttribute("usuario")).isEqualTo("5");
        });
    }

    @Test
    @DisplayName("Ida y vuelta: cada nodo vuelve al plan con lo mismo, y uno sin plazo sin ninguno")
    void idaYVuelta() {
        DiagramaArmado armado = conPlazos();

        PlanBpmn plan = importar(exportar(armado.diagrama()));

        assertThat(nodo(plan, "Activity_" + armado.id("Receive order")).plazo()).isEqualTo(OCHO_HORAS);
        assertThat(nodo(plan, "Event_" + armado.id("Start")).plazo()).isEqualTo(CINCO_DIAS);
        assertThat(nodo(plan, "Event_" + armado.id("Six hours")).escalamiento()).isEqualTo(A_ANA);
        assertThat(nodo(plan, "Activity_" + armado.id("Pick and pack items")).plazo()).isNull();
        assertThat(plan.nodos()).filteredOn(nodo -> nodo.plazo() != null).hasSize(2);
        assertThat(plan.informe().convertidos()).filteredOn(h -> h.detalle().contains("plazo")
                || h.detalle().contains("escalamiento")).isEmpty();
    }

    @Test
    @DisplayName("Un supervisor que no se dice se escribe sin usuario y vuelve como el dueno del proceso")
    void sinSupervisor() {
        DiagramaArmado armado = conPlazos();
        armado.escalamiento("Six hours", new EscalamientoResponse(AccionDeEscalamiento.AVISAR, null));

        byte[] xml = exportar(armado.diagrama());

        assertThat(elementos(leer(xml), "escalamiento")).singleElement()
                .satisfies(escalamiento -> assertThat(escalamiento.hasAttribute("usuario")).isFalse());
        assertThat(nodo(importar(xml), "Event_" + armado.id("Six hours")).escalamiento())
                .isEqualTo(new EscalamientoResponse(AccionDeEscalamiento.AVISAR, null));
    }

    @Test
    @DisplayName("En un nodo que no lo lleva se deja fuera con su nota")
    void dondeNoVa() {
        PlanBpmn plan = importar(proceso(
                "<bpmn:serviceTask id=\"S\">" + extension("<bpm:plazo cantidad=\"2\" unidad=\"HORAS\"/>")
                        + "</bpmn:serviceTask>"
                        + "<bpmn:endEvent id=\"F\">" + extension("<bpm:plazo cantidad=\"2\" unidad=\"HORAS\"/>")
                        + "</bpmn:endEvent>"
                        + "<bpmn:userTask id=\"U\">" + extension("<bpm:escalamiento accion=\"AVISAR\"/>")
                        + "</bpmn:userTask>"
                        + "<bpmn:boundaryEvent id=\"B\" attachedToRef=\"U\">"
                        + extension("<bpm:escalamiento accion=\"AVISAR\"/>")
                        + "<bpmn:timerEventDefinition><bpmn:timeDuration>PT1H</bpmn:timeDuration>"
                        + "</bpmn:timerEventDefinition></bpmn:boundaryEvent>"));

        assertThat(List.of("S", "F", "U")).allSatisfy(id -> assertThat(nodo(plan, id).plazo()).isNull());
        assertThat(nodo(plan, "U").escalamiento()).isNull();
        assertThat(nodo(plan, "B").escalamiento()).isNull();
        assertThat(nodo(plan, "B").interrumpe()).isTrue();
        assertThat(plan.informe().convertidos()).filteredOn(h -> h.detalle().contains("plazo")
                || h.detalle().contains("escalamiento")).extracting(Hallazgo::elemento).containsExactly("S", "F", "U",
                        "B");
    }

    @Test
    @DisplayName("Una unidad que no existe, una cantidad que no es numero o fuera de su tope, o una accion que no "
            + "existe: se dejan fuera con su nota")
    void loQueNoSeEntiende() {
        PlanBpmn plan = importar(proceso(
                "<bpmn:userTask id=\"A\">" + extension("<bpm:plazo cantidad=\"2\" unidad=\"SEMANAS\"/>")
                        + "</bpmn:userTask>"
                        + "<bpmn:userTask id=\"B\">" + extension("<bpm:plazo cantidad=\"dos\" unidad=\"HORAS\"/>")
                        + "</bpmn:userTask>"
                        + "<bpmn:userTask id=\"C\">" + extension("<bpm:plazo cantidad=\"367\" unidad=\"DIAS_HABILES\"/>")
                        + "</bpmn:userTask>"
                        + "<bpmn:userTask id=\"D\">" + extension("<bpm:plazo cantidad=\"0\" unidad=\"HORAS\"/>")
                        + "</bpmn:userTask>"
                        + "<bpmn:userTask id=\"E\">" + extension("<bpm:plazo cantidad=\" 8784 \" unidad=\" HORAS \"/>")
                        + "</bpmn:userTask>"
                        + "<bpmn:boundaryEvent id=\"X\" cancelActivity=\"false\" attachedToRef=\"E\">"
                        + extension("<bpm:escalamiento accion=\"DESPEDIR\" usuario=\"5\"/>")
                        + "<bpmn:timerEventDefinition><bpmn:timeDuration>PT1H</bpmn:timeDuration>"
                        + "</bpmn:timerEventDefinition></bpmn:boundaryEvent>"
                        + "<bpmn:boundaryEvent id=\"Y\" cancelActivity=\"false\" attachedToRef=\"E\">"
                        + extension("<bpm:escalamiento accion=\" AVISAR \" usuario=\"ana\"/>")
                        + "<bpmn:timerEventDefinition><bpmn:timeDuration>PT2H</bpmn:timeDuration>"
                        + "</bpmn:timerEventDefinition></bpmn:boundaryEvent>"));

        assertThat(List.of("A", "B", "C", "D")).allSatisfy(id -> assertThat(nodo(plan, id).plazo()).isNull());
        assertThat(nodo(plan, "E").plazo()).isEqualTo(new PlazoResponse(8784, UnidadDePlazo.HORAS));
        assertThat(nodo(plan, "X").escalamiento()).isNull();
        assertThat(nodo(plan, "Y").escalamiento())
                .isEqualTo(new EscalamientoResponse(AccionDeEscalamiento.AVISAR, null));
        List<Hallazgo> notas = plan.informe().convertidos().stream().filter(h -> h.detalle().contains("plazo")
                || h.detalle().contains("escalamiento")).toList();
        assertThat(notas).extracting(Hallazgo::elemento).containsExactly("A", "B", "C", "D", "X");
        assertThat(notas).extracting(Hallazgo::elemento, Hallazgo::detalle).contains(
                tuple("A", "El plazo no dice una unidad que exista (HORAS o DIAS_HABILES) con una cantidad dentro de "
                        + "su tope: se deja fuera."),
                tuple("X", "El escalamiento no dice una accion que exista (AVISAR o REASIGNAR): el borde solo abre su "
                        + "camino."));
    }

    /** La demo con un inicio simple de cinco dias habiles, una tarea de ocho horas y un borde que la escala. */
    private static DiagramaArmado conPlazos() {
        DiagramaArmado armado = DiagramaArmado.demo();
        armado.evento(DiagramaArmado.VENTAS, "Start", TipoEvento.INICIO);
        armado.arco("Start", "Receive order");
        armado.plazo("Start", CINCO_DIAS);
        armado.plazo("Receive order", OCHO_HORAS);
        armado.bordeQueNoInterrumpe(DiagramaArmado.VENTAS, "Six hours", TipoTemporizador.DURACION, "PT6H",
                "Receive order");
        armado.escalamiento("Six hours", A_ANA);
        return armado;
    }

    private static String extension(String contenido) {
        return "<bpmn:extensionElements>" + contenido + "</bpmn:extensionElements>";
    }

    private static byte[] exportar(DiagramaResponse diagrama) {
        return EscritorBpmn.escribir(diagrama, GeometriaDelDiagrama.de(diagrama));
    }

    private static PlanBpmn importar(byte[] xml) {
        EntradaBpmn.Leida leida = EntradaBpmn.leer(new ByteArrayInputStream(xml), LimitesXml.BPMN);
        return ConversionBpmn.convertir(LectorBpmn.leer(leida.bytes()), List.of(), leida.codificacion());
    }

    private static PlanBpmn importar(String xml) {
        return importar(xml.getBytes(StandardCharsets.UTF_8));
    }

    private static String proceso(String contenido) {
        return CABECERA + "<bpmn:process id=\"P\" isExecutable=\"true\">" + contenido + "</bpmn:process>"
                + "</bpmn:definitions>";
    }

    private static PlanBpmn.Nodo nodo(PlanBpmn plan, String id) {
        return plan.nodos().stream().filter(nodo -> id.equals(nodo.bpmnId())).findFirst().orElseThrow();
    }

    private static Document leer(byte[] xml) {
        return SecureXml.documento(new ByteArrayInputStream(xml), LimitesXml.BPMN);
    }

    private static List<Element> elementos(Document documento, String nombre) {
        NodeList nodos = documento.getElementsByTagNameNS(BPM, nombre);
        List<Element> lista = new ArrayList<>();
        for (int i = 0; i < nodos.getLength(); i++) {
            lista.add((Element) nodos.item(i));
        }
        return lista;
    }
}
