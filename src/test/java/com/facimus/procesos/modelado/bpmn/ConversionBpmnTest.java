package com.facimus.procesos.modelado.bpmn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.xml.LimitesXml;
import com.facimus.procesos.common.xml.XmlRechazadoException;
import com.facimus.procesos.gestion.model.ModoProceso;
import com.facimus.procesos.modelado.bpmn.InformeBpmn.Hallazgo;
import com.facimus.procesos.modelado.model.DireccionAsociacion;
import com.facimus.procesos.modelado.model.TipoActividad;
import com.facimus.procesos.modelado.model.TipoArtefacto;
import com.facimus.procesos.modelado.model.TipoAsociacion;
import com.facimus.procesos.modelado.model.TipoEvento;
import com.facimus.procesos.modelado.model.TipoTemporizador;

/** PR 54: las decisiones de la conversion, una por una, con archivos chicos escritos para cada caso. */
class ConversionBpmnTest {

    private static final String CABECERA = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<bpmn:definitions xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\" id=\"D\" "
            + "targetNamespace=\"urn:prueba\">";

    @Test
    @DisplayName("Un flujo que llega a un inicio, o que sale de un fin, rechaza el archivo con los dos errores")
    void flujosQueRompenLosEventos() {
        String xml = proceso("<bpmn:startEvent id=\"I\"/><bpmn:task id=\"T\"/><bpmn:endEvent id=\"F\"/>"
                + "<bpmn:sequenceFlow id=\"a\" sourceRef=\"I\" targetRef=\"T\"/>"
                + "<bpmn:sequenceFlow id=\"b\" sourceRef=\"T\" targetRef=\"F\"/>"
                + "<bpmn:sequenceFlow id=\"c\" sourceRef=\"F\" targetRef=\"T\"/>"
                + "<bpmn:sequenceFlow id=\"d\" sourceRef=\"T\" targetRef=\"I\"/>");

        assertThatThrownBy(() -> convertir(xml)).isInstanceOf(ReglaNegocioException.class)
                .satisfies(error -> assertThat(((ReglaNegocioException) error).getErrores()).hasSize(2)
                        .anySatisfy(texto -> assertThat(texto).contains("c").contains("fin"))
                        .anySatisfy(texto -> assertThat(texto).contains("d").contains("inicio")));
    }

    @Test
    @DisplayName("Un flujo de un pool a otro y un mensaje dentro de un pool tambien rechazan el archivo")
    void entrePools() {
        String xml = CABECERA + "<bpmn:collaboration id=\"C\">"
                + "<bpmn:participant id=\"PA\" name=\"Store\" processRef=\"A\"/>"
                + "<bpmn:participant id=\"PB\" name=\"Carrier\" processRef=\"B\"/>"
                + "<bpmn:messageFlow id=\"m\" sourceRef=\"TA\" targetRef=\"FA\"/></bpmn:collaboration>"
                + "<bpmn:process id=\"A\" isExecutable=\"true\"><bpmn:startEvent id=\"IA\"/><bpmn:sendTask id=\"TA\"/>"
                + "<bpmn:endEvent id=\"FA\"/><bpmn:sequenceFlow id=\"x\" sourceRef=\"TA\" targetRef=\"TB\"/></bpmn:process>"
                + "<bpmn:process id=\"B\"><bpmn:task id=\"TB\"/></bpmn:process></bpmn:definitions>";

        assertThatThrownBy(() -> convertir(xml)).isInstanceOf(ReglaNegocioException.class)
                .satisfies(error -> assertThat(((ReglaNegocioException) error).getErrores())
                        .anySatisfy(texto -> assertThat(texto).contains("x").contains("cruza"))
                        .anySatisfy(texto -> assertThat(texto).contains("m").contains("a si mismo")));
    }

    @Test
    @DisplayName("Lo que el modelo no tiene se convierte a lo mas cercano y el informe dice que y como")
    void conversiones() {
        PlanBpmn plan = convertir(proceso("<bpmn:startEvent id=\"I\"><bpmn:signalEventDefinition/></bpmn:startEvent>"
                + "<bpmn:manualTask id=\"M\" name=\"Pack\"/><bpmn:scriptTask id=\"S\" name=\"Score\"/>"
                + "<bpmn:endEvent id=\"F\"><bpmn:errorEventDefinition/></bpmn:endEvent>"
                + "<bpmn:sequenceFlow id=\"a\" sourceRef=\"I\" targetRef=\"M\"/>"
                + "<bpmn:sequenceFlow id=\"b\" sourceRef=\"M\" targetRef=\"S\"/>"
                + "<bpmn:sequenceFlow id=\"c\" sourceRef=\"S\" targetRef=\"F\"/>"));

        assertThat(nodo(plan, "M").tipoActividad()).isEqualTo(TipoActividad.USUARIO);
        assertThat(nodo(plan, "S").tipoActividad()).isEqualTo(TipoActividad.SERVICIO);
        assertThat(nodo(plan, "I").tipoEvento()).isEqualTo(TipoEvento.INICIO);
        assertThat(nodo(plan, "F").tipoEvento()).isEqualTo(TipoEvento.FIN);
        assertThat(plan.informe().convertidos()).extracting(Hallazgo::elemento).contains("M", "S", "I", "F");
    }

    @Test
    @DisplayName("Una espera de senal se deja fuera, y con ella los flujos que la tocaban")
    void noSoportado() {
        PlanBpmn plan = convertir(proceso("<bpmn:startEvent id=\"I\"/><bpmn:intermediateCatchEvent id=\"E\">"
                + "<bpmn:signalEventDefinition/></bpmn:intermediateCatchEvent><bpmn:endEvent id=\"F\"/>"
                + "<bpmn:sequenceFlow id=\"a\" sourceRef=\"I\" targetRef=\"E\"/>"
                + "<bpmn:sequenceFlow id=\"b\" sourceRef=\"E\" targetRef=\"F\"/>"));

        assertThat(plan.nodos()).extracting(PlanBpmn.Nodo::bpmnId).containsExactlyInAnyOrder("I", "F");
        assertThat(plan.arcos()).isEmpty();
        assertThat(plan.informe().noSoportados()).extracting(Hallazgo::elemento).containsExactlyInAnyOrder("E", "a",
                "b");
    }

    @Test
    @DisplayName("La unica salida sin condicion de un gateway pasa a ser la de por defecto")
    void salidaPorDefecto() {
        PlanBpmn plan = convertir(proceso("<bpmn:startEvent id=\"I\"/><bpmn:exclusiveGateway id=\"G\"/>"
                + "<bpmn:endEvent id=\"F1\"/><bpmn:endEvent id=\"F2\"/>"
                + "<bpmn:sequenceFlow id=\"a\" sourceRef=\"I\" targetRef=\"G\"/>"
                + "<bpmn:sequenceFlow id=\"si\" sourceRef=\"G\" targetRef=\"F1\"><bpmn:conditionExpression>"
                + "total &gt; 10</bpmn:conditionExpression></bpmn:sequenceFlow>"
                + "<bpmn:sequenceFlow id=\"no\" sourceRef=\"G\" targetRef=\"F2\"/>"));

        assertThat(plan.arcos()).filteredOn(PlanBpmn.Arco::porDefecto).extracting(PlanBpmn.Arco::bpmnId)
                .containsExactly("no");
        assertThat(plan.arcos()).filteredOn(arco -> "si".equals(arco.bpmnId())).singleElement()
                .satisfies(arco -> assertThat(arco.condicion()).isEqualTo("total > 10"));
    }

    @Test
    @DisplayName("Una condicion en otro lenguaje se conserva, avisando que hay que revisarla")
    void otroLenguaje() {
        PlanBpmn plan = convertir(proceso("<bpmn:startEvent id=\"I\"/><bpmn:exclusiveGateway id=\"G\" default=\"no\"/>"
                + "<bpmn:endEvent id=\"F1\"/><bpmn:endEvent id=\"F2\"/>"
                + "<bpmn:sequenceFlow id=\"a\" sourceRef=\"I\" targetRef=\"G\"/>"
                + "<bpmn:sequenceFlow id=\"si\" sourceRef=\"G\" targetRef=\"F1\"><bpmn:conditionExpression "
                + "language=\"juel\">${total &gt; 10}</bpmn:conditionExpression></bpmn:sequenceFlow>"
                + "<bpmn:sequenceFlow id=\"no\" sourceRef=\"G\" targetRef=\"F2\"/>"));

        assertThat(plan.arcos()).filteredOn(arco -> "si".equals(arco.bpmnId())).singleElement()
                .satisfies(arco -> assertThat(arco.condicion()).isEqualTo("${total > 10}"));
        assertThat(plan.informe().convertidos()).extracting(Hallazgo::detalle)
                .anySatisfy(detalle -> assertThat(detalle).contains("juel"));
    }

    @Test
    @DisplayName("Un id que no sirve o que se repite toma uno nuevo, y las referencias lo siguen")
    void ids() {
        PlanBpmn plan = convertir(proceso("<bpmn:startEvent id=\"1 inicio\"/><bpmn:task id=\"T\"/>"
                + "<bpmn:endEvent id=\"T\"/>"
                + "<bpmn:sequenceFlow id=\"a\" sourceRef=\"1 inicio\" targetRef=\"T\"/>"));

        assertThat(plan.nodos()).extracting(PlanBpmn.Nodo::bpmnId).doesNotHaveDuplicates()
                .allSatisfy(id -> assertThat(id).matches("[A-Za-z_][A-Za-z0-9_.-]*"));
        PlanBpmn.Arco arco = plan.arcos().getFirst();
        assertThat(plan.nodos()).extracting(PlanBpmn.Nodo::bpmnId).contains(arco.origen(), arco.destino());
        assertThat(plan.informe().convertidos()).extracting(Hallazgo::detalle)
                .filteredOn(detalle -> detalle.startsWith("Su id no sirve")).hasSize(2);
    }

    @Test
    @DisplayName("Sin dibujo, cada nodo va a la derecha del que le precede en el flujo")
    void sinDibujo() {
        PlanBpmn plan = convertir(proceso("<bpmn:startEvent id=\"I\"/><bpmn:task id=\"A\"/><bpmn:task id=\"B\"/>"
                + "<bpmn:endEvent id=\"F\"/><bpmn:sequenceFlow id=\"a\" sourceRef=\"I\" targetRef=\"A\"/>"
                + "<bpmn:sequenceFlow id=\"b\" sourceRef=\"A\" targetRef=\"B\"/>"
                + "<bpmn:sequenceFlow id=\"c\" sourceRef=\"B\" targetRef=\"F\"/>"));

        assertThat(List.of(nodo(plan, "I").x(), nodo(plan, "A").x(), nodo(plan, "B").x(), nodo(plan, "F").x()))
                .isSorted().doesNotHaveDuplicates().startsWith(0);
    }

    @Test
    @DisplayName("Un mensaje anclado a una tarea que no lo manda sale del borde de su pool")
    void mensajeDesdeElBorde() {
        String xml = CABECERA + "<bpmn:collaboration id=\"C\">"
                + "<bpmn:participant id=\"PA\" name=\"Store\" processRef=\"A\"/>"
                + "<bpmn:participant id=\"PB\" name=\"Customer\"/>"
                + "<bpmn:messageFlow id=\"m\" name=\"Notice\" sourceRef=\"T\" targetRef=\"PB\"/></bpmn:collaboration>"
                + "<bpmn:process id=\"A\" isExecutable=\"true\"><bpmn:userTask id=\"T\" name=\"Call\"/></bpmn:process>"
                + "</bpmn:definitions>";

        PlanBpmn plan = convertir(xml);

        assertThat(plan.mensajes()).singleElement().satisfies(mensaje -> {
            assertThat(mensaje.nodoOrigen()).isNull();
            assertThat(mensaje.poolOrigen()).isEqualTo("PA");
            assertThat(mensaje.contenido()).isEqualTo("Notice");
        });
    }

    @Test
    @DisplayName("Un archivo sin procesos no se importa; uno que no es BPMN, tampoco")
    void sinNada() {
        assertThatThrownBy(() -> convertir(CABECERA + "</bpmn:definitions>")).isInstanceOf(ReglaNegocioException.class);
        assertThatThrownBy(() -> convertir("<?xml version=\"1.0\"?><svg xmlns=\"http://www.w3.org/2000/svg\"/>"))
                .isInstanceOf(XmlRechazadoException.class).hasMessageContaining("no es BPMN");
    }

    @Test
    @DisplayName("Un archivo en ISO-8859-1 con tildes se lee igual; uno en UTF-7 sigue sin pasar")
    void codificaciones() {
        String latino = "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>" + proceso("<bpmn:task id=\"T\" name=\"Revisión\"/>")
                .substring(CABECERA.indexOf("<bpmn:definitions"));
        byte[] bytes = latino.getBytes(StandardCharsets.ISO_8859_1);

        EntradaBpmn.Leida leida = EntradaBpmn.leer(new ByteArrayInputStream(bytes), LimitesXml.BPMN);
        PlanBpmn plan = ConversionBpmn.convertir(LectorBpmn.leer(leida.bytes()), List.of(), leida.codificacion());

        assertThat(nodo(plan, "T").nombre()).isEqualTo("Revisión");
        assertThat(leida.codificacion()).isEqualTo("ISO-8859-1");
        byte[] utf7 = "<?xml version=\"1.0\" encoding=\"UTF-7\"?><x/>".getBytes(StandardCharsets.US_ASCII);
        assertThatThrownBy(() -> EntradaBpmn.leer(new ByteArrayInputStream(utf7), LimitesXml.BPMN))
                .isInstanceOf(XmlRechazadoException.class).hasMessageContaining("UTF-7");
    }

    @Test
    @DisplayName("Los temporizadores, el fin de terminacion y la tarea sin tipo entran con lo que dicen")
    void descriptivos() {
        PlanBpmn plan = convertir(proceso("<bpmn:startEvent id=\"I\"><bpmn:timerEventDefinition><bpmn:timeCycle>R3/PT1H"
                + "</bpmn:timeCycle></bpmn:timerEventDefinition></bpmn:startEvent><bpmn:task id=\"T\" name=\"Work\"/>"
                + "<bpmn:intermediateCatchEvent id=\"E\"><bpmn:timerEventDefinition><bpmn:timeDate>2026-12-24T09:00:00Z"
                + "</bpmn:timeDate></bpmn:timerEventDefinition></bpmn:intermediateCatchEvent>"
                + "<bpmn:endEvent id=\"F\"><bpmn:terminateEventDefinition/></bpmn:endEvent>"
                + "<bpmn:boundaryEvent id=\"B\" attachedToRef=\"T\" cancelActivity=\"false\">"
                + "<bpmn:timerEventDefinition><bpmn:timeDuration>PT2H</bpmn:timeDuration></bpmn:timerEventDefinition>"
                + "</bpmn:boundaryEvent>"));

        assertThat(nodo(plan, "I").tipoEvento()).isEqualTo(TipoEvento.TEMPORIZADOR_INICIO);
        assertThat(nodo(plan, "I").tipoTemporizador()).isEqualTo(TipoTemporizador.CICLO);
        assertThat(nodo(plan, "I").temporizador()).isEqualTo("R3/PT1H");
        assertThat(nodo(plan, "T").tipoActividad()).isEqualTo(TipoActividad.SIN_TIPO);
        assertThat(nodo(plan, "E").tipoEvento()).isEqualTo(TipoEvento.TEMPORIZADOR_INTERMEDIO);
        assertThat(nodo(plan, "E").tipoTemporizador()).isEqualTo(TipoTemporizador.FECHA);
        assertThat(nodo(plan, "F").tipoEvento()).isEqualTo(TipoEvento.FIN_TERMINACION);
        PlanBpmn.Nodo borde = nodo(plan, "B");
        assertThat(borde.tipoEvento()).isEqualTo(TipoEvento.TEMPORIZADOR_BORDE);
        assertThat(borde.adjuntoA()).isEqualTo("T");
        assertThat(borde.interrumpe()).isFalse();
        assertThat(borde.lane()).isEqualTo(nodo(plan, "T").lane());
        assertThat(borde.tipoTemporizador()).isEqualTo(TipoTemporizador.DURACION);
    }

    @Test
    @DisplayName("Un evento de borde que no es de temporizador, o sin actividad, se deja fuera con sus flujos")
    void bordesQueNoEntran() {
        PlanBpmn plan = convertir(proceso("<bpmn:userTask id=\"T\"/><bpmn:endEvent id=\"F\"/>"
                + "<bpmn:boundaryEvent id=\"B1\" attachedToRef=\"T\"><bpmn:errorEventDefinition/></bpmn:boundaryEvent>"
                + "<bpmn:boundaryEvent id=\"B2\" attachedToRef=\"Nada\"><bpmn:timerEventDefinition/></bpmn:boundaryEvent>"
                + "<bpmn:sequenceFlow id=\"a\" sourceRef=\"B2\" targetRef=\"F\"/>"));

        assertThat(plan.nodos()).extracting(PlanBpmn.Nodo::bpmnId).containsExactlyInAnyOrder("T", "F");
        assertThat(plan.arcos()).isEmpty();
        assertThat(plan.informe().noSoportados()).extracting(Hallazgo::elemento).contains("B1", "B2", "a");
    }

    @Test
    @DisplayName("Un flujo que llega a un evento de borde es un error del archivo")
    void flujoAUnBorde() {
        String xml = proceso("<bpmn:userTask id=\"T\"/><bpmn:boundaryEvent id=\"B\" attachedToRef=\"T\">"
                + "<bpmn:timerEventDefinition/></bpmn:boundaryEvent>"
                + "<bpmn:sequenceFlow id=\"a\" sourceRef=\"T\" targetRef=\"B\"/>");

        assertThatThrownBy(() -> convertir(xml)).isInstanceOf(ReglaNegocioException.class)
                .satisfies(error -> assertThat(((ReglaNegocioException) error).getErrores())
                        .anySatisfy(texto -> assertThat(texto).contains("evento de borde")));
    }

    @Test
    @DisplayName("isExecutable dice el modo; si el archivo no lo dice, tampoco lo dice el plan")
    void modo() {
        String sinDecir = CABECERA + "<bpmn:process id=\"P\"><bpmn:startEvent id=\"I\"/></bpmn:process>"
                + "</bpmn:definitions>";

        assertThat(convertir(proceso("<bpmn:startEvent id=\"I\"/>")).modo()).isEqualTo(ModoProceso.EJECUTABLE);
        assertThat(convertir(sinDecir).modo()).isNull();
        PlanBpmn documental = convertir(sinDecir.replace("<bpmn:process id=\"P\">",
                "<bpmn:process id=\"P\" isExecutable=\"false\">"));
        assertThat(documental.modo()).isEqualTo(ModoProceso.DOCUMENTAL);
        assertThat(documental.informe().convertidos()).extracting(Hallazgo::detalle)
                .anySatisfy(detalle -> assertThat(detalle).contains("documental"));
    }

    @Test
    @DisplayName("Las anotaciones, los grupos y los datos entran, con lo que los une")
    void artefactos() {
        PlanBpmn plan = convertir(CABECERA + "<bpmn:category id=\"C\"><bpmn:categoryValue id=\"V\" value=\"Checks\"/>"
                + "</bpmn:category><bpmn:process id=\"P\" isExecutable=\"false\"><bpmn:task id=\"T\">"
                + "<bpmn:dataInputAssociation id=\"in\"><bpmn:sourceRef>R</bpmn:sourceRef><bpmn:targetRef>X</bpmn:targetRef>"
                + "</bpmn:dataInputAssociation><bpmn:dataOutputAssociation id=\"out\"><bpmn:targetRef>S</bpmn:targetRef>"
                + "</bpmn:dataOutputAssociation></bpmn:task><bpmn:dataObject id=\"O\"/>"
                + "<bpmn:dataObjectReference id=\"R\" name=\"Order\" dataObjectRef=\"O\"/>"
                + "<bpmn:dataStoreReference id=\"S\" name=\"Stock\"/>"
                + "<bpmn:textAnnotation id=\"A\"><bpmn:text>Check by hand</bpmn:text></bpmn:textAnnotation>"
                + "<bpmn:group id=\"G\" categoryValueRef=\"V\"/>"
                + "<bpmn:association id=\"as\" sourceRef=\"A\" targetRef=\"T\" associationDirection=\"One\"/>"
                + "</bpmn:process></bpmn:definitions>");

        assertThat(plan.artefactos()).extracting(PlanBpmn.Artefacto::bpmnId, PlanBpmn.Artefacto::tipo,
                PlanBpmn.Artefacto::texto).containsExactlyInAnyOrder(
                        tuple("R", TipoArtefacto.OBJETO_DATOS, "Order"),
                        tuple("S", TipoArtefacto.ALMACEN_DATOS, "Stock"),
                        tuple("A", TipoArtefacto.ANOTACION, "Check by hand"),
                        tuple("G", TipoArtefacto.GRUPO, "Checks"));
        assertThat(plan.asociaciones()).extracting(PlanBpmn.Asociacion::bpmnId, PlanBpmn.Asociacion::tipo,
                PlanBpmn.Asociacion::origen, PlanBpmn.Asociacion::destino, PlanBpmn.Asociacion::direccion)
                .containsExactlyInAnyOrder(
                        tuple("in", TipoAsociacion.ENTRADA_DATOS, "R", "T", DireccionAsociacion.NINGUNA),
                        tuple("out", TipoAsociacion.SALIDA_DATOS, "T", "S", DireccionAsociacion.NINGUNA),
                        tuple("as", TipoAsociacion.ASOCIACION, "A", "T", DireccionAsociacion.UNA));
        assertThat(plan.informe().soportados()).containsEntry("artefactos", 4).containsEntry("asociaciones", 3);
    }

    @Test
    @DisplayName("Una asociacion sin artefacto, o con un extremo que no entra, se deja fuera")
    void asociacionesQueNoEntran() {
        PlanBpmn plan = convertir(proceso("<bpmn:userTask id=\"T\"/><bpmn:endEvent id=\"F\"/>"
                + "<bpmn:textAnnotation id=\"A\"/>"
                + "<bpmn:association id=\"sinArtefacto\" sourceRef=\"T\" targetRef=\"F\"/>"
                + "<bpmn:association id=\"colgando\" sourceRef=\"A\" targetRef=\"Nada\"/>"));

        assertThat(plan.asociaciones()).isEmpty();
        assertThat(plan.informe().noSoportados()).extracting(Hallazgo::elemento).contains("sinArtefacto", "colgando");
    }

    @Test
    @DisplayName("Un artefacto dibujado dentro de una lane queda en ella; fuera de todo, queda suelto")
    void artefactosPorSuDibujo() {
        PlanBpmn plan = convertir(CABECERA + "<bpmn:collaboration id=\"K\"><bpmn:participant id=\"Pa\" processRef=\"P\"/>"
                + "<bpmn:textAnnotation id=\"Fuera\"/></bpmn:collaboration>"
                + "<bpmn:process id=\"P\" isExecutable=\"true\"><bpmn:laneSet id=\"LS\"><bpmn:lane id=\"L\" name=\"Sales\">"
                + "<bpmn:flowNodeRef>T</bpmn:flowNodeRef></bpmn:lane></bpmn:laneSet><bpmn:userTask id=\"T\"/>"
                + "<bpmn:textAnnotation id=\"Dentro\"/></bpmn:process>"
                + "<bpmndi:BPMNDiagram xmlns:bpmndi=\"http://www.omg.org/spec/BPMN/20100524/DI\" "
                + "xmlns:dc=\"http://www.omg.org/spec/DD/20100524/DC\"><bpmndi:BPMNPlane bpmnElement=\"K\">"
                + forma("Pa", 0, 0, 800, 200) + forma("L", 30, 0, 770, 200) + forma("T", 100, 50, 100, 80)
                + forma("Dentro", 300, 60, 100, 30) + forma("Fuera", 900, 500, 100, 30)
                + "</bpmndi:BPMNPlane></bpmndi:BPMNDiagram></bpmn:definitions>");

        PlanBpmn.Artefacto dentro = artefacto(plan, "Dentro");
        assertThat(dentro.lane()).isEqualTo("L");
        assertThat(dentro.pool()).isEqualTo("Pa");
        // El centro de la tarea es (150, 90): la esquina de la anotacion, en el marco de los nodos
        assertThat(dentro.x()).isEqualTo(300 + 64 - 150);
        assertThat(dentro.y()).isEqualTo(60 + 28 - 90);
        assertThat(dentro.ancho()).isEqualTo(100);
        PlanBpmn.Artefacto fuera = artefacto(plan, "Fuera");
        assertThat(fuera.lane()).isNull();
        assertThat(fuera.pool()).isNull();
        assertThat(fuera.x()).isZero();
        assertThat(fuera.y()).isZero();
    }

    @Test
    @DisplayName("Un subproceso entra con su contenido, en su lane, y uno vacio se queda como esta")
    void subprocesos() {
        PlanBpmn plan = convertir(proceso("<bpmn:subProcess id=\"S\"><bpmn:incoming>x</bpmn:incoming>"
                + "<bpmn:startEvent id=\"I\"/><bpmn:userTask id=\"T\"/>"
                + "<bpmn:sequenceFlow id=\"a\" sourceRef=\"I\" targetRef=\"T\"/></bpmn:subProcess>"
                + "<bpmn:subProcess id=\"V\"/>"));

        assertThat(nodo(plan, "S").tipoActividad()).isEqualTo(TipoActividad.SUBPROCESO);
        assertThat(nodo(plan, "V").tipoActividad()).isEqualTo(TipoActividad.SUBPROCESO_COLAPSADO);
        assertThat(nodo(plan, "T").padre()).isEqualTo("S");
        assertThat(nodo(plan, "T").lane()).isEqualTo(nodo(plan, "S").lane());
        assertThat(plan.arcos()).extracting(PlanBpmn.Arco::bpmnId).containsExactly("a");
        assertThat(plan.informe().noSoportados()).isEmpty();
        assertThat(plan.informe().convertidos()).extracting(Hallazgo::detalle)
                .anySatisfy(detalle -> assertThat(detalle).contains("se despliega"));
    }

    @Test
    @DisplayName("Un flujo que cruza el borde de un subproceso es un error del archivo")
    void flujoCruzaElBorde() {
        String xml = proceso("<bpmn:startEvent id=\"I\"/><bpmn:subProcess id=\"S\"><bpmn:userTask id=\"T\"/>"
                + "</bpmn:subProcess><bpmn:sequenceFlow id=\"a\" sourceRef=\"I\" targetRef=\"T\"/>");

        assertThatThrownBy(() -> convertir(xml)).isInstanceOf(ReglaNegocioException.class)
                .satisfies(error -> assertThat(((ReglaNegocioException) error).getErrores())
                        .anySatisfy(texto -> assertThat(texto).contains("cruza el borde")));
    }

    @Test
    @DisplayName("Una llamada entra con su proceso si lo nombra como este producto, y si no se avisa")
    void llamadas() {
        PlanBpmn plan = convertir(proceso("<bpmn:callActivity id=\"C\" calledElement=\"Proceso_7\"/>"
                + "<bpmn:callActivity id=\"D\" calledElement=\"other-key\"/>"
                + "<bpmn:callActivity id=\"E\" calledElement=\"Proceso_abc\"/>"));

        assertThat(nodo(plan, "C").tipoActividad()).isEqualTo(TipoActividad.LLAMADA);
        assertThat(nodo(plan, "C").procesoLlamado()).isEqualTo(7L);
        assertThat(nodo(plan, "D").procesoLlamado()).isNull();
        assertThat(nodo(plan, "E").procesoLlamado()).isNull();
        assertThat(plan.informe().convertidos()).extracting(Hallazgo::elemento).contains("D", "E");
    }

    @Test
    @DisplayName("Un subproceso de eventos se deja fuera con lo que lleva dentro")
    void subprocesoDeEventos() {
        PlanBpmn plan = convertir(proceso("<bpmn:userTask id=\"T\"/><bpmn:subProcess id=\"S\" triggeredByEvent=\"true\">"
                + "<bpmn:userTask id=\"Dentro\"/></bpmn:subProcess>"));

        assertThat(plan.nodos()).extracting(PlanBpmn.Nodo::bpmnId).containsExactly("T");
        assertThat(plan.informe().noSoportados()).extracting(Hallazgo::elemento).contains("S");
    }

    private static String forma(String elemento, int x, int y, int ancho, int alto) {
        return "<bpmndi:BPMNShape bpmnElement=\"" + elemento + "\"><dc:Bounds x=\"" + x + "\" y=\"" + y
                + "\" width=\"" + ancho + "\" height=\"" + alto + "\"/></bpmndi:BPMNShape>";
    }

    private static PlanBpmn.Artefacto artefacto(PlanBpmn plan, String id) {
        return plan.artefactos().stream().filter(uno -> uno.bpmnId().equals(id)).findFirst().orElseThrow();
    }

    private static String proceso(String contenido) {
        return CABECERA + "<bpmn:process id=\"P\" isExecutable=\"true\">" + contenido + "</bpmn:process>"
                + "</bpmn:definitions>";
    }

    private static PlanBpmn convertir(String xml) {
        EntradaBpmn.Leida leida = EntradaBpmn.leer(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)),
                LimitesXml.BPMN);
        return ConversionBpmn.convertir(LectorBpmn.leer(leida.bytes()), List.of(), leida.codificacion());
    }

    private static PlanBpmn.Nodo nodo(PlanBpmn plan, String id) {
        return plan.nodos().stream().filter(nodo -> id.equals(nodo.bpmnId())).findFirst().orElseThrow();
    }
}
