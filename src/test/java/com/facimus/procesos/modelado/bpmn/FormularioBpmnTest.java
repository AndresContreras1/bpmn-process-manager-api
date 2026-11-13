package com.facimus.procesos.modelado.bpmn;

import static com.facimus.procesos.common.formularios.CampoArmado.campo;
import static com.facimus.procesos.common.formularios.CampoArmado.formulario;
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

import com.facimus.procesos.common.formularios.CampoDeFormulario;
import com.facimus.procesos.common.formularios.Formulario;
import com.facimus.procesos.common.formularios.TipoDeCampo;
import com.facimus.procesos.common.xml.LimitesXml;
import com.facimus.procesos.common.xml.SecureXml;
import com.facimus.procesos.modelado.bpmn.InformeBpmn.Hallazgo;
import com.facimus.procesos.modelado.dto.response.DiagramaResponse;
import com.facimus.procesos.modelado.model.TipoActividad;
import com.facimus.procesos.modelado.model.TipoEvento;
import com.facimus.procesos.modelado.service.DiagramaArmado;

/** PR 69: el formulario sale como bpm:formulario, valida contra el XSD y vuelve entero al importarlo. */
class FormularioBpmnTest {

    private static final String BPM = EspaciosBpmn.BPM;
    private static final String CABECERA = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<bpmn:definitions xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\" "
            + "xmlns:bpm=\"urn:bpmn-process-manager:bpm:1\" id=\"D\" targetNamespace=\"urn:prueba\">";

    /** Un campo de cada tipo, con cada propiedad que admite. */
    private static final Formulario COMPLETO = formulario(
            campo("datos", TipoDeCampo.SECCION).etiqueta("Datos & condiciones").visibleSi("order.total > 0"),
            campo("nombre", TipoDeCampo.TEXTO).requerido().largo(3, 120).patron("[A-Z][a-z]+ \\d{2}")
                    .ayuda("Como en la factura <tal cual>"),
            campo("notas", TipoDeCampo.TEXTO_LARGO).largo(0, 4_000),
            campo("cantidad", TipoDeCampo.NUMERO).minimo("-1.5").maximo("1000"),
            campo("monto", TipoDeCampo.MONEDA).moneda("COP").minimo("0").porDefecto("order.total"),
            campo("dia", TipoDeCampo.FECHA).entre("2026-01-01", "2026-12-31"),
            campo("cita", TipoDeCampo.FECHA_HORA).entre("2026-01-01T08:00:00", null),
            campo("urgente", TipoDeCampo.SI_NO),
            campo("metodo", TipoDeCampo.LISTA).opciones("TARJETA", "EFECTIVO")
                    .requeridoSi("urgente == true and nombre != 'x'"),
            campo("extras", TipoDeCampo.LISTA_MULTIPLE).opciones("A", "B").minimo("1").maximo("2"),
            campo("responsable", TipoDeCampo.PERSONA),
            campo("soporte", TipoDeCampo.ARCHIVO));

    @Test
    @DisplayName("Una tarea y un inicio con formulario validan contra los XSD de la OMG y el de bpm:")
    void valida() {
        assertThat(EsquemaBpmn.problemas(exportar(conFormularios().diagrama()))).isEmpty();
    }

    @Test
    @DisplayName("Cada campo sale con sus propiedades como atributos y sus opciones; lo compilado no se escribe")
    void loQueSale() {
        Document documento = leer(exportar(conFormularios().diagrama()));

        List<Element> formularios = elementos(documento, BPM, "formulario");
        assertThat(formularios).hasSize(2);
        assertThat(formularios).extracting(formulario -> ((Element) formulario.getParentNode().getParentNode())
                .getLocalName()).containsExactlyInAnyOrder("userTask", "startEvent");
        List<Element> campos = elementos(documento, BPM, "campoFormulario");
        Element cantidad = campos.stream().filter(campo -> "cantidad".equals(campo.getAttribute("nombre")))
                .findFirst().orElseThrow();
        assertThat(cantidad.getAttribute("tipo")).isEqualTo("NUMERO");
        assertThat(cantidad.getAttribute("maximo")).isEqualTo("1000");
        assertThat(cantidad.getAttribute("minimo")).isEqualTo("-1.5");
        assertThat(cantidad.hasAttribute("requerido")).isFalse();
        Element nombre = campos.stream().filter(campo -> "nombre".equals(campo.getAttribute("nombre")))
                .findFirst().orElseThrow();
        assertThat(nombre.getAttribute("requerido")).isEqualTo("true");
        assertThat(nombre.getAttribute("patron")).isEqualTo("[A-Z][a-z]+ \\d{2}");
        assertThat(nombre.getAttribute("ayuda")).isEqualTo("Como en la factura <tal cual>");
        assertThat(elementos(documento, BPM, "opcion")).extracting(opcion -> opcion.getAttribute("valor"))
                .containsExactly("TARJETA", "EFECTIVO", "A", "B");
        assertThat(documento.getElementsByTagNameNS("*", "visibleSiArbol").getLength()).isZero();
        assertThat(new String(exportar(conFormularios().diagrama()), StandardCharsets.UTF_8))
                .doesNotContain("Arbol");
    }

    @Test
    @DisplayName("Un decimal de XML no lleva exponente: 1E+3 sale como 1000")
    void numerosPlanos() {
        DiagramaArmado armado = DiagramaArmado.demo();
        armado.formulario("Receive order", formulario(campo("a", TipoDeCampo.NUMERO).minimo("-2.50")
                .maximo("1E+3")));
        byte[] xml = exportar(armado.diagrama());

        Element campo = elementos(leer(xml), BPM, "campoFormulario").getFirst();

        assertThat(campo.getAttribute("maximo")).isEqualTo("1000");
        assertThat(campo.getAttribute("minimo")).isEqualTo("-2.50");
        assertThat(EsquemaBpmn.problemas(xml)).isEmpty();
    }

    @Test
    @DisplayName("Ida y vuelta: lo que se exporta vuelve al plan igual que estaba, campo por campo")
    void idaYVuelta() {
        DiagramaArmado armado = conFormularios();
        String tarea = "Activity_" + armado.id("Receive order");
        String inicio = "Event_" + armado.id("Started by hand");

        PlanBpmn plan = importar(exportar(armado.diagrama()));

        assertThat(nodo(plan, tarea).formulario()).isEqualTo(COMPLETO);
        assertThat(nodo(plan, inicio).formulario()).isEqualTo(formulario(campo("pedido", TipoDeCampo.TEXTO)));
        assertThat(plan.nodos()).filteredOn(nodo -> nodo.formulario() != null).hasSize(2);
    }

    @Test
    @DisplayName("Un formulario en una tarea que no es de usuario, o en un inicio que no es simple, se deja fuera")
    void dondeNoVa() {
        String formulario = "<bpmn:extensionElements><bpm:formulario><bpm:campoFormulario nombre=\"a\" "
                + "etiqueta=\"A\" tipo=\"TEXTO\"/></bpm:formulario></bpmn:extensionElements>";
        PlanBpmn plan = importar(proceso("<bpmn:serviceTask id=\"S\">" + formulario + "</bpmn:serviceTask>"
                + "<bpmn:startEvent id=\"M\">" + formulario + "<bpmn:messageEventDefinition/></bpmn:startEvent>"
                + "<bpmn:userTask id=\"U\">" + formulario + "</bpmn:userTask>"));

        assertThat(nodo(plan, "S").formulario()).isNull();
        assertThat(nodo(plan, "M").formulario()).isNull();
        assertThat(nodo(plan, "U").formulario().campos()).hasSize(1);
        assertThat(plan.informe().convertidos()).filteredOn(hallazgo -> hallazgo.detalle().contains("formulario"))
                .extracting(Hallazgo::elemento).containsExactlyInAnyOrder("S", "M");
    }

    @Test
    @DisplayName("Un campo sin un tipo que exista se deja fuera con su nota; si no queda ninguno, no hay formulario")
    void tipoQueNoExiste() {
        PlanBpmn plan = importar(proceso("<bpmn:userTask id=\"U\"><bpmn:extensionElements><bpm:formulario>"
                + "<bpm:campoFormulario nombre=\"a\" etiqueta=\"A\" tipo=\"COLOR\"/>"
                + "<bpm:campoFormulario nombre=\"b\" etiqueta=\"B\" tipo=\" SI_NO \"/>"
                + "<bpm:campoFormulario nombre=\"c\" etiqueta=\"C\"/>"
                + "</bpm:formulario></bpmn:extensionElements></bpmn:userTask>"
                + "<bpmn:userTask id=\"V\"><bpmn:extensionElements><bpm:formulario>"
                + "<bpm:campoFormulario nombre=\"a\" etiqueta=\"A\" tipo=\"COLOR\"/>"
                + "</bpm:formulario></bpmn:extensionElements></bpmn:userTask>"));

        assertThat(nodo(plan, "U").formulario().campos()).extracting(CampoDeFormulario::nombre).containsExactly("b");
        assertThat(nodo(plan, "V").formulario()).isNull();
        assertThat(plan.informe().convertidos()).filteredOn(hallazgo -> hallazgo.detalle().contains("tipo"))
                .extracting(Hallazgo::detalle).containsExactly(
                        "El campo \"a\" del formulario no tiene un tipo que exista: se deja fuera.",
                        "El campo \"c\" del formulario no tiene un tipo que exista: se deja fuera.",
                        "El campo \"a\" del formulario no tiene un tipo que exista: se deja fuera.");
    }

    @Test
    @DisplayName("Mas de cien campos: se quedan los primeros cien, con su nota")
    void tope() {
        StringBuilder campos = new StringBuilder();
        for (int i = 0; i < 101; i++) {
            campos.append("<bpm:campoFormulario nombre=\"c").append(i).append("\" etiqueta=\"C\" tipo=\"TEXTO\"/>");
        }
        PlanBpmn plan = importar(proceso("<bpmn:userTask id=\"U\"><bpmn:extensionElements><bpm:formulario>"
                + campos + "</bpm:formulario></bpmn:extensionElements></bpmn:userTask>"));

        assertThat(nodo(plan, "U").formulario().campos()).hasSize(100).last()
                .extracting(CampoDeFormulario::nombre).isEqualTo("c99");
        assertThat(plan.informe().convertidos()).extracting(Hallazgo::detalle)
                .contains("El formulario trae mas de 100 campos: se quedan los primeros.");
    }

    @Test
    @DisplayName("Lo que no se lee como numero llega vacio: un exponente, un texto o un entero partido")
    void numerosIlegibles() {
        PlanBpmn plan = importar(proceso("<bpmn:userTask id=\"U\"><bpmn:extensionElements><bpm:formulario>"
                + "<bpm:campoFormulario nombre=\"a\" etiqueta=\"A\" tipo=\"NUMERO\" minimo=\"1E+999999999\" "
                + "maximo=\"mucho\" requerido=\"si\"/>"
                + "<bpm:campoFormulario nombre=\"b\" etiqueta=\"B\" tipo=\"TEXTO\" largoMinimo=\"2.5\" "
                + "largoMaximo=\" 40 \"/>"
                + "<bpm:campoFormulario nombre=\"c\" etiqueta=\"C\" tipo=\"NUMERO\" minimo=\" -0.25 \"/>"
                + "</bpm:formulario></bpmn:extensionElements></bpmn:userTask>"));

        List<CampoDeFormulario> campos = nodo(plan, "U").formulario().campos();
        assertThat(campos.get(0).minimo()).isNull();
        assertThat(campos.get(0).maximo()).isNull();
        assertThat(campos.get(0).requerido()).isNull();
        assertThat(campos.get(1).largoMinimo()).isNull();
        assertThat(campos.get(1).largoMaximo()).isEqualTo(40);
        assertThat(campos.get(2).minimo()).isEqualByComparingTo("-0.25");
    }

    @Test
    @DisplayName("Un nodo sin bpm:formulario no tiene formulario, aunque traiga otras extensiones")
    void sinFormulario() {
        PlanBpmn plan = importar(proceso("<bpmn:userTask id=\"U\"><bpmn:extensionElements>"
                + "<bpm:lane rol=\"x\" orden=\"0\"/></bpmn:extensionElements></bpmn:userTask>"
                + "<bpmn:userTask id=\"V\"/>"));

        assertThat(nodo(plan, "U").formulario()).isNull();
        assertThat(nodo(plan, "V").formulario()).isNull();
    }

    /** La demo con su tarea de usuario y un inicio simple con formulario. */
    private static DiagramaArmado conFormularios() {
        DiagramaArmado armado = DiagramaArmado.demo();
        armado.formulario("Receive order", COMPLETO);
        armado.evento(DiagramaArmado.VENTAS, "Started by hand", TipoEvento.INICIO);
        armado.actividad(DiagramaArmado.VENTAS, "Check by hand", TipoActividad.USUARIO);
        armado.arco("Started by hand", "Check by hand");
        armado.arco("Check by hand", "Order cancelled");
        armado.formulario("Started by hand", formulario(campo("pedido", TipoDeCampo.TEXTO)));
        return armado;
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

    private static List<Element> elementos(Document documento, String espacio, String nombre) {
        NodeList nodos = documento.getElementsByTagNameNS(espacio, nombre);
        List<Element> lista = new ArrayList<>();
        for (int i = 0; i < nodos.getLength(); i++) {
            lista.add((Element) nodos.item(i));
        }
        return lista;
    }
}
