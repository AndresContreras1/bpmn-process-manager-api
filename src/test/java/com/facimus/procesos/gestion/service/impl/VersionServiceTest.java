package com.facimus.procesos.gestion.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.metricas.MetricasDeNegocio;
import com.facimus.procesos.common.model.Empresa;
import com.facimus.procesos.gestion.dto.response.VersionResponse;
import com.facimus.procesos.gestion.mapper.VersionProcesoMapper;
import com.facimus.procesos.gestion.model.EstadoVersion;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.model.VersionProceso;
import com.facimus.procesos.gestion.repository.ProcesoRepository;
import com.facimus.procesos.gestion.repository.UsuarioRepository;
import com.facimus.procesos.gestion.repository.VersionProcesoRepository;
import com.facimus.procesos.gestion.service.HistorialCambioService;
import com.facimus.procesos.gestion.service.ModeloBpmn;

/**
 * Lo que se lee de las versiones publicadas: solo las de un proceso activo de la tienda, su definicion, su archivo
 * BPMN con el nombre del proceso y el numero, y el de la vigente para quien lo tiene compartido.
 */
@ExtendWith(MockitoExtension.class)
class VersionServiceTest {

    private static final Long EMPRESA = 7L;
    private static final Long PROCESO = 10L;

    @Mock
    private VersionProcesoRepository versionProcesoRepository;
    @Mock
    private DefinicionesDeVersion definiciones;
    @Mock
    private ProcesoRepository procesoRepository;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private HistorialCambioService historialCambioService;
    @Mock
    private VersionProcesoMapper versionProcesoMapper;
    @Mock
    private MetricasDeNegocio metricas;

    @InjectMocks
    private VersionServiceImpl service;

    private final Proceso proceso = Proceso.builder().id(PROCESO).empresa(Empresa.builder().id(EMPRESA).build())
            .nombre("Order fulfillment").activo(true).build();

    @Test
    @DisplayName("Las versiones de un proceso se listan y se leen por numero")
    void listarYObtener() {
        conProceso();
        VersionProceso version = VersionProceso.builder().id(5L).proceso(proceso).numero(2).build();
        List<VersionResponse> respuestas = List.of(mock(VersionResponse.class));
        VersionResponse respuesta = mock(VersionResponse.class);
        when(versionProcesoRepository.findAllByProcesoIdAndEmpresaIdOrderByNumeroDesc(PROCESO, EMPRESA))
                .thenReturn(List.of(version));
        when(versionProcesoMapper.toResponses(List.of(version))).thenReturn(respuestas);
        when(versionProcesoRepository.findByProcesoIdAndNumeroAndEmpresaId(PROCESO, 2, EMPRESA))
                .thenReturn(Optional.of(version));
        when(versionProcesoMapper.toResponse(version)).thenReturn(respuesta);

        assertThat(service.listar(EMPRESA, PROCESO)).isSameAs(respuestas);
        assertThat(service.obtener(EMPRESA, PROCESO, 2)).isSameAs(respuesta);
        assertThatThrownBy(() -> service.obtener(EMPRESA, PROCESO, 3))
                .isInstanceOf(RecursoNoEncontradoException.class).hasMessage("Versión no encontrada.");
    }

    @Test
    @DisplayName("La definicion de una version sale de su fila; una que no existe es un 404")
    void definicion() {
        conProceso();
        when(versionProcesoRepository.idDeLaVersion(PROCESO, 2, EMPRESA)).thenReturn(Optional.of(5L));
        when(definiciones.de(EMPRESA, 5L)).thenReturn("{\"pools\":[]}");

        assertThat(service.definicion(EMPRESA, PROCESO, 2)).isEqualTo("{\"pools\":[]}");
        assertThatThrownBy(() -> service.definicion(EMPRESA, PROCESO, 3))
                .isInstanceOf(RecursoNoEncontradoException.class).hasMessage("Versión no encontrada.");
    }

    @Test
    @DisplayName("El archivo BPMN de una version lleva el nombre del proceso y el numero")
    void modelo() {
        conProceso();
        when(versionProcesoRepository.idDeLaVersion(PROCESO, 2, EMPRESA)).thenReturn(Optional.of(5L));
        when(versionProcesoRepository.modeloBpmnDe(5L, EMPRESA)).thenReturn(Optional.of("<definitions/>"));

        ModeloBpmn modelo = service.modelo(EMPRESA, PROCESO, 2);

        assertThat(modelo).isEqualTo(ModeloBpmn.deLaVersion("Order fulfillment", 2, "<definitions/>"));
        assertThatThrownBy(() -> service.modelo(EMPRESA, PROCESO, 3)).isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("Una version guardada antes de que hubiera archivo BPMN no tiene modelo que descargar")
    void modeloSinArchivo() {
        conProceso();
        when(versionProcesoRepository.idDeLaVersion(PROCESO, 1, EMPRESA)).thenReturn(Optional.of(4L));

        assertThatThrownBy(() -> service.modelo(EMPRESA, PROCESO, 1)).isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("El modelo vigente es el de la ultima version vigente; sin ella, no hay")
    void modeloVigente() {
        VersionProceso vigente = VersionProceso.builder().id(5L).proceso(proceso).numero(3)
                .modeloBpmn("<definitions/>").build();
        when(versionProcesoRepository.findFirstByProcesoIdAndEmpresaIdAndEstadoOrderByNumeroDesc(PROCESO, EMPRESA,
                EstadoVersion.VIGENTE)).thenReturn(Optional.of(vigente));

        assertThat(service.modeloVigente(EMPRESA, PROCESO))
                .contains(ModeloBpmn.deLaVersion("Order fulfillment", 3, "<definitions/>"));
        assertThat(service.modeloVigente(EMPRESA, 11L)).isEmpty();
    }

    @Test
    @DisplayName("Las versiones de un proceso que no es activo de la tienda no se leen")
    void procesoAjeno() {
        assertThatThrownBy(() -> service.listar(EMPRESA, PROCESO)).isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage("Proceso no encontrado.");
        assertThatThrownBy(() -> service.definicion(EMPRESA, PROCESO, 1))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> service.modelo(EMPRESA, PROCESO, 1)).isInstanceOf(RecursoNoEncontradoException.class);
        verify(versionProcesoRepository, never()).idDeLaVersion(anyLong(), anyInt(), anyLong());
    }

    @Test
    @DisplayName("PR 62: el dibujo de una version se guarda saneado una sola vez, y despues se lee igual")
    void dibujo() {
        conProceso();
        VersionProceso version = VersionProceso.builder().id(5L).proceso(proceso).numero(2).build();
        when(versionProcesoRepository.findByProcesoIdAndNumeroAndEmpresaId(PROCESO, 2, EMPRESA))
                .thenReturn(Optional.of(version));
        byte[] svg = ("<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script>"
                + "<rect width=\"1\" height=\"1\"/></svg>").getBytes(StandardCharsets.UTF_8);

        assertThat(service.dibujo(EMPRESA, PROCESO, 2)).isEmpty();
        service.guardarDibujo(EMPRESA, PROCESO, 2, svg);

        assertThat(version.getSvg()).contains("<rect").doesNotContain("script");
        assertThat(service.dibujo(EMPRESA, PROCESO, 2)).contains(version.getSvg());
        verify(versionProcesoRepository).save(version);
        assertThatThrownBy(() -> service.guardarDibujo(EMPRESA, PROCESO, 2, svg))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("ya tiene su dibujo");
    }

    private void conProceso() {
        when(procesoRepository.findByIdAndEmpresaIdAndActivoTrue(PROCESO, EMPRESA)).thenReturn(Optional.of(proceso));
    }
}
