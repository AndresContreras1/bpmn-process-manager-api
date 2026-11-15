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

import com.facimus.procesos.common.formularios.CampoArmado;
import com.facimus.procesos.common.formularios.TipoDeCampo;
import com.facimus.procesos.common.xml.LimitesXml;
import com.facimus.procesos.common.xml.SecureXml;
import com.facimus.procesos.modelado.bpmn.InformeBpmn.Hallazgo;
import com.facimus.procesos.modelado.dto.response.AsignacionResponse;
import com.facimus.procesos.modelado.dto.response.DiagramaResponse;
import com.facimus.procesos.modelado.model.TipoActividad;
import com.facimus.procesos.modelado.model.TipoAsignacion;
import com.facimus.procesos.modelado.service.DiagramaArmado;

/** PR 70: a quien le llega una tarea sale como bpm:asignacion, valida contra el XSD y vuelve entera al importarla. */
class AsignacionBpmnTest {

    private static final String BPM = EspaciosBpmn.BPM;
    private static final String CABECERA = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<bpmn:definitions xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\" "
            + "xmlns:bpm=\"urn:bpmn-process-manager:bpm:1\" id=\"D\" targetNamespace=\"urn:prueba\">";
    private static final AsignacionResponse A_ANA = new AsignacionResponse(TipoAsignacion.PERSONA, 5L, null);
    private static final AsignacionResponse AL_CAMPO =
            new AsignacionResponse(TipoAsignacion.CAMPO, null, "tarea.receiveOrder.aprobador");
    private static final AsignacionResponse AL_INICIADOR = new AsignacionResponse(TipoAsignacion.INICIADOR, null, null);

    @Test
    @DisplayName("Las tres asignaciones, una junto a un formulario, validan contra los XSD de la OMG y el de bpm:")
    void valida() {
        assertThat(EsquemaBpmn.problemas(exportar(conAsignaciones().diagrama()))).isEmpty();
    }

    @Test
    @DisplayName("Cada una sale con su tipo y lo suyo, en la userTask; la de la bandeja del rol no se escribe")
    void loQueSale() {
        Document documento = leer(exportar(conAsignaciones().diagrama()));

        List<Element> asignaciones = elementos(documento, "asignacion");
        assertThat(asignaciones).hasSize(3).allSatisfy(asignacion -> assertThat(
                ((Element) asignacion.getParentNode().getParentNode()).getLocalName()).isEqualTo("userTask"));
        assertThat(asignaciones).extracting(asignacion -> asignacion.getAttribute("tipo"))
                .containsExactlyInAnyOrder("PERSONA", "CAMPO", "INICIADOR");
        Element persona = asignaciones.stream().filter(a -> "PERSONA".equals(a.getAttribute("tipo"))).findFirst()
                .orElseThrow();
        assertThat(persona.getAttribute("usuario")).isEqualTo("5");
        assertThat(persona.hasAttribute("campo")).isFalse();
        Element campo = asignaciones.stream().filter(a -> "CAMPO".equals(a.getAttribute("tipo"))).findFirst()
                .orElseThrow();
        assertThat(campo.getAttribute("campo")).isEqualTo("tarea.receiveOrder.aprobador");
        assertThat(campo.hasAttribute("usuario")).isFalse();
        assertThat(elementos(documento, "formulario")).hasSize(1);
    }

    @Test
    @DisplayName("Ida y vuelta: cada tarea vuelve al plan con la misma asignacion, y la del rol sin ninguna")
    void idaYVuelta() {
        DiagramaArmado armado = conAsignaciones();

        PlanBpmn plan = importar(exportar(armado.diagrama()));

        assertThat(nodo(plan, "Activity_" + armado.id("Receive order")).asignacion()).isEqualTo(AL_CAMPO);
        assertThat(nodo(plan, "Activity_" + armado.id("Pick and pack items")).asignacion()).isEqualTo(A_ANA);
        assertThat(nodo(plan, "Activity_" + armado.id("Approve by hand")).asignacion()).isEqualTo(AL_INICIADOR);
        assertThat(nodo(plan, "Activity_" + armado.id("Receive order")).formulario()).isNotNull();
        assertThat(plan.nodos()).filteredOn(nodo -> nodo.asignacion() != null).hasSize(3);
        assertThat(plan.informe().convertidos()).filteredOn(h -> h.detalle().contains("asignacion")).isEmpty();
    }

    @Test
    @DisplayName("En una tarea que no es de usuario se deja fuera con su nota; ROL es la de siempre y no se guarda")
    void dondeNoVa() {
        PlanBpmn plan = importar(proceso(
                "<bpmn:serviceTask id=\"S\">" + asignacion("tipo=\"INICIADOR\"") + "</bpmn:serviceTask>"
                        + "<bpmn:userTask id=\"R\">" + asignacion("tipo=\"ROL\"") + "</bpmn:userTask>"
                        + "<bpmn:userTask id=\"U\">" + asignacion("tipo=\" INICIADOR \" usuario=\"3\" campo=\"x\"")
                        + "</bpmn:userTask>"));

        assertThat(nodo(plan, "S").asignacion()).isNull();
        assertThat(nodo(plan, "R").asignacion()).isNull();
        assertThat(nodo(plan, "U").asignacion()).isEqualTo(AL_INICIADOR);
        assertThat(plan.informe().convertidos()).filteredOn(h -> h.detalle().contains("asignacion"))
                .extracting(Hallazgo::elemento, Hallazgo::detalle).containsExactly(
                        tuple("S", "Trae una asignacion, pero solo se asignan las tareas de usuario: se deja fuera."));
    }

    @Test
    @DisplayName("Un tipo que no existe, una persona que no dice quien o un campo vacio o largo: al rol, con su nota")
    void loQueNoSePuedeCumplir() {
        PlanBpmn plan = importar(proceso(
                "<bpmn:userTask id=\"T\">" + asignacion("tipo=\"JEFE\"") + "</bpmn:userTask>"
                        + "<bpmn:userTask id=\"P\">" + asignacion("tipo=\"PERSONA\" usuario=\"ana\"")
                        + "</bpmn:userTask>"
                        + "<bpmn:userTask id=\"C\">" + asignacion("tipo=\"CAMPO\" campo=\"  \"") + "</bpmn:userTask>"
                        + "<bpmn:userTask id=\"L\">" + asignacion("tipo=\"CAMPO\" campo=\"" + "a".repeat(201) + "\"")
                        + "</bpmn:userTask>"
                        + "<bpmn:userTask id=\"K\">" + asignacion("tipo=\"CAMPO\" campo=\" aprobador \" usuario=\"9\"")
                        + "</bpmn:userTask>"
                        + "<bpmn:userTask id=\"M\">" + asignacion("tipo=\"PERSONA\" usuario=\" 12 \" campo=\"x\"")
                        + "</bpmn:userTask>"));

        assertThat(List.of("T", "P", "C", "L")).allSatisfy(id -> assertThat(nodo(plan, id).asignacion()).isNull());
        assertThat(nodo(plan, "K").asignacion())
                .isEqualTo(new AsignacionResponse(TipoAsignacion.CAMPO, null, "aprobador"));
        assertThat(nodo(plan, "M").asignacion()).isEqualTo(new AsignacionResponse(TipoAsignacion.PERSONA, 12L, null));
        assertThat(plan.informe().convertidos()).filteredOn(h -> h.detalle().contains("asignacion"))
                .extracting(Hallazgo::elemento, Hallazgo::detalle).containsExactly(
                        tuple("T", "La asignacion no tiene un tipo que exista: la tarea "
                                + "espera en la bandeja de su rol."),
                        tuple("P", "La asignacion no dice a que persona: la tarea "
                                + "espera en la bandeja de su rol."),
                        tuple("C", "La asignacion no dice la variable del caso, o la "
                                + "que dice es demasiado larga: la tarea espera en la bandeja de su rol."),
                        tuple("L", "La asignacion no dice la variable del caso, o la "
                                + "que dice es demasiado larga: la tarea espera en la bandeja de su rol."));
    }

    @Test
    @DisplayName("Un campo de exactamente 200 caracteres cabe; uno sin bpm:asignacion no tiene ninguna")
    void enElBorde() {
        String doscientos = "a".repeat(200);
        PlanBpmn plan = importar(proceso("<bpmn:userTask id=\"C\">" + asignacion("tipo=\"CAMPO\" campo=\""
                + doscientos + "\"") + "</bpmn:userTask><bpmn:userTask id=\"V\"><bpmn:extensionElements>"
                + "<bpm:lane rol=\"x\" orden=\"0\"/></bpmn:extensionElements></bpmn:userTask>"
                + "<bpmn:userTask id=\"W\"/>"));

        assertThat(nodo(plan, "C").asignacion().campo()).isEqualTo(doscientos);
        assertThat(nodo(plan, "V").asignacion()).isNull();
        assertThat(nodo(plan, "W").asignacion()).isNull();
    }

    /** La demo con tres tareas de usuario asignadas de las tres maneras, una de ellas con formulario. */
    private static DiagramaArmado conAsignaciones() {
        DiagramaArmado armado = DiagramaArmado.demo();
        armado.formulario("Receive order", CampoArmado.formulario(CampoArmado.campo("aprobador",
                TipoDeCampo.PERSONA)));
        armado.asignacion("Receive order", AL_CAMPO);
        armado.asignacion("Pick and pack items", A_ANA);
        armado.actividad(DiagramaArmado.VENTAS, "Approve by hand", TipoActividad.USUARIO);
        armado.arco("Approve by hand", "Order cancelled");
        armado.asignacion("Approve by hand", AL_INICIADOR);
        return armado;
    }

    private static String asignacion(String atributos) {
        return "<bpmn:extensionElements><bpm:asignacion " + atributos + "/></bpmn:extensionElements>";
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
