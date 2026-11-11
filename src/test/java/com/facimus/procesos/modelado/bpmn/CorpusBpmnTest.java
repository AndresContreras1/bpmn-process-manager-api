package com.facimus.procesos.modelado.bpmn;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Objects;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.facimus.procesos.common.xml.LimitesXml;
import com.facimus.procesos.gestion.model.ModoProceso;
import com.facimus.procesos.modelado.bpmn.InformeBpmn.Hallazgo;
import com.facimus.procesos.modelado.model.TipoArtefacto;
import com.facimus.procesos.modelado.model.TipoAsociacion;
import com.facimus.procesos.modelado.model.TipoEvento;
import com.facimus.procesos.modelado.model.TipoParticipante;

/**
 * PR 54: el corpus dorado, archivo por archivo. {@code bpmn/miwg} es la clase A de referencia de la suite MIWG de la
 * OMG (CC BY 3.0, ver su NOTICE): cuatro en ISO-8859-1, uno sin prefijo, uno en vertical, con subprocesos y eventos de
 * borde que el modelo no tiene. {@code bpmn/herramientas} son dos diagramas dibujados con bpmn-js 18.31.1, el motor de
 * bpmn.io y de Camunda Modeler, el segundo con la extension de Camunda (camunda-bpmn-moddle 7.0.1): exportados por
 * la herramienta, no escritos a mano.
 */
class CorpusBpmnTest {

    @Test
    @DisplayName("A.1.0: en ISO-8859-1, sin lanes ni colaboracion; entra todo, con una lane por defecto")
    void a10() {
        InformeBpmn informe = plan("miwg/A.1.0.bpmn").informe();

        assertThat(informe.soportados()).isEqualTo(Map.of("pools", 1, "lanes", 1, "eventos", 2, "tareas", 3,
                "flujos", 4));
        assertThat(informe.convertidos()).extracting(Hallazgo::detalle)
                .contains("Venia en ISO-8859-1: se leyo pasado a UTF-8.")
                .anySatisfy(detalle -> assertThat(detalle).contains("lane General"));
        assertThat(informe.noSoportados()).isEmpty();
        assertThat(informe.avisosDelEsquema()).isEmpty();
    }

    @Test
    @DisplayName("A.2.0 y A.2.1: gateways sin condiciones; entran, y el diagnostico las pedira para publicar")
    void a20() {
        PlanBpmn a20 = plan("miwg/A.2.0.bpmn");
        PlanBpmn a21 = plan("miwg/A.2.1.bpmn");

        assertThat(a20.informe().soportados()).containsEntry("gateways", 2).containsEntry("flujos", 9);
        assertThat(a20.informe().convertidos()).extracting(Hallazgo::detalle)
                .anySatisfy(detalle -> assertThat(detalle).contains("3 salidas sin condicion").contains("E-07"));
        assertThat(a21.informe().soportados()).containsEntry("gateways", 2).containsEntry("flujos", 11);
        assertThat(a21.informe().noSoportados()).extracting(Hallazgo::detalle)
                .singleElement().satisfies(detalle -> assertThat(detalle).contains("ioSpecification"));
    }

    @Test
    @DisplayName("A.3.0: el subproceso entra; los eventos de borde de mensaje y escalamiento quedan fuera con sus flujos")
    void a30() {
        InformeBpmn informe = plan("miwg/A.3.0.bpmn").informe();

        assertThat(informe.soportados()).containsEntry("tareas", 5).containsEntry("flujos", 6);
        assertThat(informe.noSoportados()).extracting(Hallazgo::detalle)
                .filteredOn(detalle -> detalle.contains("boundaryEvent")).hasSize(2);
        assertThat(informe.noSoportados()).extracting(Hallazgo::detalle)
                .filteredOn(detalle -> detalle.contains("Toca un elemento que se dejo fuera")).hasSize(2);
    }

    @Test
    @DisplayName("A.4.0: un proceso que ningun participante nombra entra como pool; los mensajes salen del borde")
    void a40() {
        PlanBpmn plan = plan("miwg/A.4.0.bpmn");

        assertThat(plan.informe().soportados()).containsEntry("pools", 2).containsEntry("lanes", 3)
                .containsEntry("mensajes", 2);
        assertThat(plan.informe().convertidos()).extracting(Hallazgo::detalle)
                .contains("Un proceso que ningun participante nombra: entra como un pool mas.");
        assertThat(plan.mensajes()).allSatisfy(mensaje -> {
            assertThat(mensaje.nodoOrigen()).isNull();
            assertThat(mensaje.nodoDestino()).isNull();
        });
    }

    @Test
    @DisplayName("A.4.1: sin prefijo y en vertical; se gira, y las tareas de una lane avanzan en x")
    void a41() {
        PlanBpmn plan = plan("miwg/A.4.1.bpmn");

        assertThat(plan.informe().convertidos()).extracting(Hallazgo::detalle)
                .filteredOn(detalle -> detalle.startsWith("Se dibuja en vertical")).hasSize(2);
        // En el archivo, Start Event 1 esta arriba de Task 1, que esta arriba de Task 2: girado, van de izquierda a derecha
        assertThat(x(plan, "Start Event 1")).isLessThan(x(plan, "Task 1"));
        assertThat(x(plan, "Task 1")).isLessThan(x(plan, "Task 2"));
    }

    @Test
    @DisplayName("bpmn.io: entra todo tal cual; el cliente de caja negra se deduce por estar dibujado arriba")
    void bpmnIo() {
        PlanBpmn plan = plan("herramientas/bpmn-io.bpmn");

        assertThat(plan.informe().soportados()).isEqualTo(Map.of("pools", 2, "lanes", 2, "tareas", 2,
                "gateways", 1, "eventos", 3, "flujos", 5, "mensajes", 1));
        assertThat(plan.informe().noSoportados()).isEmpty();
        assertThat(plan.pools()).extracting(PlanBpmn.Pool::tipo)
                .containsExactly(TipoParticipante.EMPRESA, TipoParticipante.CLIENTE);
        assertThat(plan.arcos()).filteredOn(PlanBpmn.Arco::porDefecto).singleElement()
                .satisfies(arco -> assertThat(arco.etiqueta()).isEqualTo("No"));
        // Los colores que bpmn.io deja en el dibujo de la tarea se conservan
        assertThat(plan.informe().extensionesConservadas()).anySatisfy(clave -> assertThat(clave).endsWith("#di"));
    }

    @Test
    @DisplayName("bpmn.io con lo descriptivo: temporizadores, terminacion, datos, anotacion y grupo entran tal cual")
    void bpmnIoDescriptivo() {
        PlanBpmn plan = plan("herramientas/bpmn-io-descriptivo.bpmn");

        assertThat(plan.informe().noSoportados()).isEmpty();
        assertThat(plan.modo()).isEqualTo(ModoProceso.DOCUMENTAL);
        assertThat(plan.nodos()).extracting(PlanBpmn.Nodo::tipoEvento).filteredOn(Objects::nonNull)
                .containsExactlyInAnyOrder(TipoEvento.TEMPORIZADOR_INICIO, TipoEvento.TEMPORIZADOR_INTERMEDIO,
                        TipoEvento.TEMPORIZADOR_BORDE, TipoEvento.FIN_TERMINACION, TipoEvento.FIN);
        assertThat(plan.nodos()).filteredOn(nodo -> nodo.tipoEvento() == TipoEvento.TEMPORIZADOR_BORDE).singleElement()
                .satisfies(borde -> {
                    assertThat(borde.adjuntoA()).isNotNull();
                    assertThat(borde.interrumpe()).isFalse();
                    assertThat(borde.temporizador()).isEqualTo("PT2H");
                });
        assertThat(plan.artefactos()).extracting(PlanBpmn.Artefacto::tipo).containsExactlyInAnyOrder(
                TipoArtefacto.ANOTACION, TipoArtefacto.GRUPO, TipoArtefacto.OBJETO_DATOS, TipoArtefacto.ALMACEN_DATOS);
        assertThat(plan.asociaciones()).extracting(PlanBpmn.Asociacion::tipo).containsExactlyInAnyOrder(
                TipoAsociacion.ASOCIACION, TipoAsociacion.ENTRADA_DATOS, TipoAsociacion.SALIDA_DATOS);
    }

    @Test
    @DisplayName("Camunda: lo de camunda: se conserva por elemento, en la tarea, la de envio y el proceso")
    void camunda() {
        PlanBpmn plan = plan("herramientas/camunda-modeler.bpmn");

        String tarea = plan.nodos().stream().filter(nodo -> "Review order".equals(nodo.nombre())).findFirst()
                .orElseThrow().bpmnId();
        String envio = plan.nodos().stream().filter(nodo -> "Ship order".equals(nodo.nombre())).findFirst()
                .orElseThrow().bpmnId();
        assertThat(plan.extensiones().get(tarea)).contains("camunda:assignee=\"ana\"").contains("camunda:properties")
                .contains("name=\"sla\"");
        assertThat(plan.extensiones().get(envio)).contains("camunda:topic=\"shipping\"");
        assertThat(plan.extensiones()).hasEntrySatisfying(plan.pools().getFirst().bpmnId() + "#process",
                envoltorio -> assertThat(envoltorio).contains("camunda:historyTimeToLive=\"180\""));
    }

    private static PlanBpmn plan(String archivo) {
        try (InputStream entrada = CorpusBpmnTest.class.getResourceAsStream("/bpmn/" + archivo)) {
            EntradaBpmn.Leida leida = EntradaBpmn.leer(entrada, LimitesXml.BPMN);
            return ConversionBpmn.convertir(LectorBpmn.leer(leida.bytes()), EsquemaBpmn.problemas(leida.bytes()),
                    leida.codificacion());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static int x(PlanBpmn plan, String nombre) {
        return plan.nodos().stream().filter(nodo -> nombre.equals(nodo.nombre())).findFirst().orElseThrow().x();
    }
}
