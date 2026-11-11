package com.facimus.procesos.modelado;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.facimus.procesos.common.model.RolAcceso;
import com.facimus.procesos.gestion.repository.UsuarioRepository;
import com.facimus.procesos.gestion.service.EmpresaService;
import com.facimus.procesos.modelado.bpmn.EsquemaBpmn;
import com.facimus.procesos.modelado.dto.response.ImportacionResponse;
import com.facimus.procesos.modelado.service.ImportacionBpmnService;
import com.facimus.procesos.modelado.service.ModeloBpmnService;

/**
 * PR 54: la ida y vuelta de cada archivo del corpus dorado. Se importa como proceso nuevo; lo que ese proceso exporta
 * valida contra la OMG; importado de vuelta en el mismo proceso no cambia nada, y lo exportado despues es igual.
 */
@SpringBootTest
@ActiveProfiles("test")
class CorpusBpmnIntegracionTest {

    private static int tiendas;

    @Autowired
    private EmpresaService empresaService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private ImportacionBpmnService importacionBpmnService;

    @Autowired
    private ModeloBpmnService modeloBpmnService;

    private Long empresaId;
    private Long adminId;

    @BeforeEach
    void tienda() {
        int numero = ++tiendas;
        String admin = "admin" + numero + "@corpus.test";
        empresaId = empresaService.registrar("Tienda del corpus " + numero, "9063" + numero + "000-1", admin,
                "Cora Corpus", admin, "marea-violeta-del-sur").id();
        adminId = usuarioRepository.findByEmail(admin).orElseThrow().getId();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"miwg/A.1.0.bpmn", "miwg/A.2.0.bpmn", "miwg/A.2.1.bpmn", "miwg/A.3.0.bpmn",
        "miwg/A.4.0.bpmn", "miwg/A.4.1.bpmn", "herramientas/bpmn-io.bpmn", "herramientas/bpmn-io-descriptivo.bpmn", "herramientas/camunda-modeler.bpmn"})
    @DisplayName("Importar, exportar e importar de vuelta: el archivo exportado valida y la vuelta no cambia nada")
    void idaYVuelta(String archivo) {
        ImportacionResponse importacion = importacionBpmnService.importar(empresaId, adminId, RolAcceso.ADMINISTRADOR,
                recurso(archivo), archivo);
        String exportado = modeloBpmnService.exportar(empresaId, importacion.procesoId()).xml();

        assertThat(importacion.creados()).isPositive();
        assertThat(EsquemaBpmn.problemas(exportado.getBytes(StandardCharsets.UTF_8))).isEmpty();
        ImportacionResponse vuelta = importacionBpmnService.reemplazar(empresaId, adminId, RolAcceso.ADMINISTRADOR,
                importacion.procesoId(), entrada(exportado));
        assertThat(vuelta.creados() + vuelta.editados() + vuelta.dadosDeBaja()).isZero();
        // Lo unico que se repite es el aviso de las condiciones que faltan: el archivo exportado sigue sin ellas
        assertThat(vuelta.informe().convertidos()).allSatisfy(hallazgo ->
                assertThat(hallazgo.detalle()).contains("E-07"));
        assertThat(vuelta.informe().noSoportados()).isEmpty();
        assertThat(modeloBpmnService.exportar(empresaId, importacion.procesoId()).xml()).isEqualTo(exportado);
    }

    @Test
    @DisplayName("Lo de Camunda vuelve intacto al exportar: atributos, propiedades, el proceso y los colores")
    void camundaVuelveIntacto() {
        ImportacionResponse importacion = importacionBpmnService.importar(empresaId, adminId, RolAcceso.ADMINISTRADOR,
                recurso("herramientas/camunda-modeler.bpmn"), "Camunda");

        String exportado = modeloBpmnService.exportar(empresaId, importacion.procesoId()).xml();

        assertThat(exportado)
                .contains("xmlns:camunda=\"http://camunda.org/schema/1.0/bpmn\"")
                .contains("camunda:assignee=\"ana\"")
                .contains("camunda:formKey=\"embedded:app:forms/review.html\"")
                .contains("<camunda:properties>")
                .contains("<camunda:property name=\"sla\" value=\"2h\"/>")
                .contains("camunda:topic=\"shipping\"")
                .contains("camunda:historyTimeToLive=\"180\"")
                .contains("bioc:fill=\"#bbdefb\"")
                .contains("color:background-color=\"#bbdefb\"");
    }

    private static InputStream recurso(String archivo) {
        try (InputStream entrada = CorpusBpmnIntegracionTest.class.getResourceAsStream("/bpmn/" + archivo)) {
            return new ByteArrayInputStream(entrada.readAllBytes());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static ByteArrayInputStream entrada(String xml) {
        return new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
    }
}
