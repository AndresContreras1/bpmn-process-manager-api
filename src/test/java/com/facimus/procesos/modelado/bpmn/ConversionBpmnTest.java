package com.facimus.procesos.modelado.bpmn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.xml.LimitesXml;
import com.facimus.procesos.common.xml.XmlRechazadoException;
import com.facimus.procesos.modelado.bpmn.InformeBpmn.Hallazgo;
import com.facimus.procesos.modelado.model.TipoActividad;
import com.facimus.procesos.modelado.model.TipoEvento;

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
        PlanBpmn plan = convertir(proceso("<bpmn:startEvent id=\"I\"><bpmn:timerEventDefinition/></bpmn:startEvent>"
                + "<bpmn:manualTask id=\"M\" name=\"Pack\"/><bpmn:scriptTask id=\"S\" name=\"Score\"/>"
                + "<bpmn:endEvent id=\"F\"><bpmn:terminateEventDefinition/></bpmn:endEvent>"
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
    @DisplayName("Una espera de temporizador se deja fuera, y con ella los flujos que la tocaban")
    void noSoportado() {
        PlanBpmn plan = convertir(proceso("<bpmn:startEvent id=\"I\"/><bpmn:intermediateCatchEvent id=\"E\">"
                + "<bpmn:timerEventDefinition/></bpmn:intermediateCatchEvent><bpmn:endEvent id=\"F\"/>"
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
