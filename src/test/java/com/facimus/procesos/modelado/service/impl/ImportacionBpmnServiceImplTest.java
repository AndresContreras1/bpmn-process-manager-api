package com.facimus.procesos.modelado.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.facimus.procesos.common.Huella;
import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.auditoria.Auditoria;
import com.facimus.procesos.common.model.Empresa;
import com.facimus.procesos.common.model.RolAcceso;
import com.facimus.procesos.gestion.dto.response.ProcesoResponse;
import com.facimus.procesos.gestion.model.EstadoProceso;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.repository.ProcesoRepository;
import com.facimus.procesos.gestion.service.HistorialCambioService;
import com.facimus.procesos.gestion.service.ProcesoService;
import com.facimus.procesos.modelado.bpmn.PlanBpmn;
import com.facimus.procesos.modelado.dto.response.HallazgoImportacionResponse;
import com.facimus.procesos.modelado.dto.response.ImportacionResponse;
import com.facimus.procesos.modelado.model.Importacion;
import com.facimus.procesos.modelado.repository.ImportacionRepository;

import tools.jackson.databind.json.JsonMapper;

/**
 * La tuberia de una importacion con un archivo real y la aplicacion simulada: que nombre, descripcion y categoria
 * lleva un proceso importado, que el informe dice lo convertido y lo dejado fuera, y que rastro queda: el historial
 * solo si algo cambio, y la importacion con la bitacora siempre, salvo en un autoguardado que no cambio nada.
 */
@ExtendWith(MockitoExtension.class)
class ImportacionBpmnServiceImplTest {

    private static final Long EMPRESA_ID = 1L;
    private static final Long USUARIO_ID = 2L;
    private static final Long PROCESO_ID = 10L;

    /** Un proceso con nombre, descripcion y categoria; una tarea manual que se convierte y una senal que no entra. */
    private static final String ARCHIVO = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<bpmn:definitions xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\" "
            + "xmlns:bpm=\"urn:bpmn-process-manager:bpm:1\" id=\"D\" targetNamespace=\"urn:prueba\">"
            + "<bpmn:collaboration id=\"C\" name=\"Returns\">"
            + "<bpmn:documentation>From the request to the refund</bpmn:documentation>"
            + "<bpmn:extensionElements><bpm:proceso categoria=\"After sales\"/></bpmn:extensionElements>"
            + "<bpmn:participant id=\"P\" name=\"Demo Store\" processRef=\"Proc\"/></bpmn:collaboration>"
            + "<bpmn:process id=\"Proc\" isExecutable=\"true\">"
            + "<bpmn:startEvent id=\"I\"/><bpmn:manualTask id=\"M\" name=\"Pack\"/><bpmn:endEvent id=\"F\"/>"
            + "<bpmn:intermediateCatchEvent id=\"E\"><bpmn:signalEventDefinition/></bpmn:intermediateCatchEvent>"
            + "<bpmn:sequenceFlow id=\"a\" sourceRef=\"I\" targetRef=\"M\"/>"
            + "<bpmn:sequenceFlow id=\"b\" sourceRef=\"M\" targetRef=\"F\"/>"
            + "</bpmn:process></bpmn:definitions>";

    /** Un proceso sin nombre, sin descripcion y sin categoria. */
    private static final String ARCHIVO_SIN_NOMBRE = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<bpmn:definitions xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\" id=\"D\" "
            + "targetNamespace=\"urn:prueba\"><bpmn:process id=\"Proc\" isExecutable=\"true\">"
            + "<bpmn:startEvent id=\"I\"/><bpmn:userTask id=\"T\"/><bpmn:endEvent id=\"F\"/>"
            + "<bpmn:sequenceFlow id=\"a\" sourceRef=\"I\" targetRef=\"T\"/>"
            + "<bpmn:sequenceFlow id=\"b\" sourceRef=\"T\" targetRef=\"F\"/>"
            + "</bpmn:process></bpmn:definitions>";

    @Mock
    private ProcesoService procesoService;

    @Mock
    private ProcesoRepository procesoRepository;

    @Mock
    private AplicacionDelModelo aplicacion;

    @Mock
    private ImportacionRepository importacionRepository;

    @Mock
    private HistorialCambioService historialCambioService;

    @Mock
    private Auditoria auditoria;

    private ImportacionBpmnServiceImpl servicio;
    private Empresa tienda;
    private Proceso proceso;

    @BeforeEach
    void elServicio() {
        servicio = new ImportacionBpmnServiceImpl(procesoService, procesoRepository, aplicacion, importacionRepository,
                historialCambioService, auditoria, JsonMapper.builder().build());
        tienda = Empresa.builder().id(EMPRESA_ID).nombre("Demo Store").build();
        proceso = Proceso.builder().id(PROCESO_ID).empresa(tienda).nombre("Returns").build();
    }

    @Test
    @DisplayName("Importar crea el proceso con el nombre pedido sin espacios, y la descripcion y categoria del archivo")
    void importar_conNombre_creaElProcesoYDejaRastro() {
        creaElProceso("Store returns", "From the request to the refund", "After sales");
        given(aplicacion.aplicar(eq(EMPRESA_ID), eq(USUARIO_ID), eq(RolAcceso.ADMINISTRADOR), eq(proceso),
                any(PlanBpmn.class))).willReturn(new AplicacionDelModelo.Cambios(3, 2, 1));

        ImportacionResponse respuesta = servicio.importar(EMPRESA_ID, USUARIO_ID, RolAcceso.ADMINISTRADOR,
                entrada(ARCHIVO), "  Store returns  ");

        assertThat(respuesta.procesoId()).isEqualTo(PROCESO_ID);
        assertThat(respuesta.nombre()).isEqualTo("Returns");
        assertThat(respuesta.creados()).isEqualTo(3);
        assertThat(respuesta.editados()).isEqualTo(2);
        assertThat(respuesta.dadosDeBaja()).isEqualTo(1);
        assertThat(respuesta.informe().convertidos()).extracting(HallazgoImportacionResponse::elemento).contains("M");
        assertThat(respuesta.informe().convertidos()).allSatisfy(hallazgo -> assertThat(hallazgo.detalle()).isNotBlank());
        assertThat(respuesta.informe().noSoportados()).extracting(HallazgoImportacionResponse::elemento).contains("E");
        assertThat(respuesta.informe().noSoportados()).allSatisfy(hallazgo -> assertThat(hallazgo.detalle()).isNotBlank());
        verify(historialCambioService).registrar(EMPRESA_ID, USUARIO_ID, proceso,
                "Modelo importado de un archivo BPMN: 3 creados, 2 editados y 1 dados de baja.");
        byte[] bytes = ARCHIVO.getBytes(StandardCharsets.UTF_8);
        ArgumentCaptor<Importacion> importacion = ArgumentCaptor.forClass(Importacion.class);
        verify(importacionRepository).save(importacion.capture());
        Importacion guardada = importacion.getValue();
        assertThat(guardada.getEmpresa()).isSameAs(tienda);
        assertThat(guardada.getProcesoId()).isEqualTo(PROCESO_ID);
        assertThat(guardada.getUsuarioId()).isEqualTo(USUARIO_ID);
        assertThat(guardada.getFecha()).isNotNull();
        assertThat(guardada.getBytes()).isEqualTo(bytes.length);
        assertThat(guardada.getHuellaArchivo()).isEqualTo(Huella.de(bytes));
        assertThat(guardada.getCreados()).isEqualTo(3);
        assertThat(guardada.getEditados()).isEqualTo(2);
        assertThat(guardada.getDadosDeBaja()).isEqualTo(1);
        assertThat(guardada.getInforme()).contains("\"M\"").contains("\"E\"");
        verify(auditoria).registrar(EMPRESA_ID, USUARIO_ID, "PROCESO_IMPORTADO", "PROCESO", PROCESO_ID,
                Map.of("creados", 3, "editados", 2, "dadosDeBaja", 1, "bytes", bytes.length));
    }

    @Test
    @DisplayName("Sin nombre pedido, o con uno en blanco, el proceso se llama como dice el archivo")
    void importar_sinNombre_usaElDelArchivo() {
        creaElProceso("Returns", "From the request to the refund", "After sales");
        given(aplicacion.aplicar(eq(EMPRESA_ID), eq(USUARIO_ID), eq(RolAcceso.ADMINISTRADOR), eq(proceso),
                any(PlanBpmn.class))).willReturn(new AplicacionDelModelo.Cambios(3, 0, 0));

        servicio.importar(EMPRESA_ID, USUARIO_ID, RolAcceso.ADMINISTRADOR, entrada(ARCHIVO), null);
        servicio.importar(EMPRESA_ID, USUARIO_ID, RolAcceso.ADMINISTRADOR, entrada(ARCHIVO), "   ");

        verify(procesoService, times(2)).crear(EMPRESA_ID, USUARIO_ID, "Returns",
                "From the request to the refund", "After sales");
    }

    @Test
    @DisplayName("Un archivo sin nombre, descripcion ni categoria da los de por defecto")
    void importar_archivoSinNombre_usaLosDePorDefecto() {
        creaElProceso("Proceso importado", "Importado de un archivo BPMN.", "General");
        given(aplicacion.aplicar(eq(EMPRESA_ID), eq(USUARIO_ID), eq(RolAcceso.ADMINISTRADOR), eq(proceso),
                any(PlanBpmn.class))).willReturn(new AplicacionDelModelo.Cambios(3, 0, 0));

        servicio.importar(EMPRESA_ID, USUARIO_ID, RolAcceso.ADMINISTRADOR, entrada(ARCHIVO_SIN_NOMBRE), null);

        verify(procesoService).crear(EMPRESA_ID, USUARIO_ID, "Proceso importado", "Importado de un archivo BPMN.",
                "General");
    }

    @Test
    @DisplayName("Reemplazar el modelo de un proceso que no existe no lee ni aplica nada")
    void reemplazar_procesoQueNoExiste() {
        given(procesoRepository.findByIdAndEmpresaIdAndActivoTrue(PROCESO_ID, EMPRESA_ID)).willReturn(Optional.empty());
        ByteArrayInputStream archivo = entrada(ARCHIVO);

        assertThatThrownBy(() -> servicio.reemplazar(EMPRESA_ID, USUARIO_ID, RolAcceso.ADMINISTRADOR, PROCESO_ID,
                archivo)).isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage("Proceso no encontrado.");
        verifyNoInteractions(aplicacion, importacionRepository, auditoria);
    }

    @Test
    @DisplayName("Reemplazar con un archivo que no cambia nada registra la importacion, pero no el historial")
    void reemplazar_sinCambios_registraLaImportacionYNoElHistorial() {
        elProcesoExiste();
        given(aplicacion.aplicar(eq(EMPRESA_ID), eq(USUARIO_ID), eq(RolAcceso.EDITOR), eq(proceso),
                any(PlanBpmn.class))).willReturn(new AplicacionDelModelo.Cambios(0, 0, 0));

        ImportacionResponse respuesta = servicio.reemplazar(EMPRESA_ID, USUARIO_ID, RolAcceso.EDITOR, PROCESO_ID,
                entrada(ARCHIVO));

        assertThat(respuesta.creados() + respuesta.editados() + respuesta.dadosDeBaja()).isZero();
        verifyNoInteractions(historialCambioService);
        verify(importacionRepository).save(any(Importacion.class));
        verify(auditoria).registrar(eq(EMPRESA_ID), eq(USUARIO_ID), eq("PROCESO_IMPORTADO"), eq("PROCESO"),
                eq(PROCESO_ID), any());
    }

    @Test
    @DisplayName("Reemplazar con un archivo que solo edita deja la linea en el historial")
    void reemplazar_conEdiciones_dejaElHistorial() {
        elProcesoExiste();
        given(aplicacion.aplicar(eq(EMPRESA_ID), eq(USUARIO_ID), eq(RolAcceso.EDITOR), eq(proceso),
                any(PlanBpmn.class))).willReturn(new AplicacionDelModelo.Cambios(0, 1, 0));

        servicio.reemplazar(EMPRESA_ID, USUARIO_ID, RolAcceso.EDITOR, PROCESO_ID, entrada(ARCHIVO));

        verify(historialCambioService).registrar(EMPRESA_ID, USUARIO_ID, proceso,
                "Modelo importado de un archivo BPMN: 0 creados, 1 editados y 0 dados de baja.");
    }

    @Test
    @DisplayName("Un autoguardado que no cambia nada no deja ningun rastro")
    void autoguardar_sinCambios_noDejaRastro() {
        elProcesoExiste();
        given(aplicacion.aplicar(eq(EMPRESA_ID), eq(USUARIO_ID), eq(RolAcceso.EDITOR), eq(proceso),
                any(PlanBpmn.class))).willReturn(new AplicacionDelModelo.Cambios(0, 0, 0));

        ImportacionResponse respuesta = servicio.autoguardar(EMPRESA_ID, USUARIO_ID, RolAcceso.EDITOR, PROCESO_ID,
                entrada(ARCHIVO));

        assertThat(respuesta.procesoId()).isEqualTo(PROCESO_ID);
        assertThat(respuesta.creados() + respuesta.editados() + respuesta.dadosDeBaja()).isZero();
        verifyNoInteractions(historialCambioService, importacionRepository, auditoria);
    }

    @Test
    @DisplayName("Un autoguardado que da algo de baja deja el historial, la importacion y la bitacora")
    void autoguardar_conCambios_dejaRastro() {
        elProcesoExiste();
        given(aplicacion.aplicar(eq(EMPRESA_ID), eq(USUARIO_ID), eq(RolAcceso.EDITOR), eq(proceso),
                any(PlanBpmn.class))).willReturn(new AplicacionDelModelo.Cambios(0, 0, 1));

        servicio.autoguardar(EMPRESA_ID, USUARIO_ID, RolAcceso.EDITOR, PROCESO_ID, entrada(ARCHIVO));

        verify(historialCambioService).registrar(EMPRESA_ID, USUARIO_ID, proceso,
                "Modelo importado de un archivo BPMN: 0 creados, 0 editados y 1 dados de baja.");
        verify(importacionRepository).save(any(Importacion.class));
        verify(auditoria).registrar(EMPRESA_ID, USUARIO_ID, "PROCESO_IMPORTADO", "PROCESO", PROCESO_ID,
                Map.of("creados", 0, "editados", 0, "dadosDeBaja", 1, "bytes",
                        ARCHIVO.getBytes(StandardCharsets.UTF_8).length));
    }

    @Test
    @DisplayName("Autoguardar en un proceso que no existe no aplica nada")
    void autoguardar_procesoQueNoExiste() {
        given(procesoRepository.findByIdAndEmpresaIdAndActivoTrue(PROCESO_ID, EMPRESA_ID)).willReturn(Optional.empty());
        ByteArrayInputStream archivo = entrada(ARCHIVO);

        assertThatThrownBy(() -> servicio.autoguardar(EMPRESA_ID, USUARIO_ID, RolAcceso.EDITOR, PROCESO_ID, archivo))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage("Proceso no encontrado.");
        verifyNoInteractions(aplicacion);
    }

    private void creaElProceso(String nombre, String descripcion, String categoria) {
        given(procesoService.crear(EMPRESA_ID, USUARIO_ID, nombre, descripcion, categoria))
                .willReturn(new ProcesoResponse(PROCESO_ID, nombre, descripcion, categoria, EstadoProceso.BORRADOR,
                        true, null, null, 0L, USUARIO_ID, null, null, false));
        elProcesoExiste();
    }

    private void elProcesoExiste() {
        given(procesoRepository.findByIdAndEmpresaIdAndActivoTrue(PROCESO_ID, EMPRESA_ID)).willReturn(Optional.of(proceso));
    }

    private static ByteArrayInputStream entrada(String xml) {
        return new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
    }
}
