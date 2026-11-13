package com.facimus.procesos.modelado.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.SolicitudInvalidaException;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.repository.ProcesoRepository;
import com.facimus.procesos.gestion.service.ProcesoService;
import com.facimus.procesos.gestion.service.VersionService;
import com.facimus.procesos.modelado.dto.response.ComparacionResponse;
import com.facimus.procesos.modelado.dto.response.DiagramaResponse;
import com.facimus.procesos.modelado.service.DiagramaArmado;
import com.facimus.procesos.modelado.service.DiagramaService;

import tools.jackson.databind.json.JsonMapper;

/**
 * Que se compara con que: el borrador sale del modelo vivo, una version de su instantanea, y vigente es la publicada.
 * Lo que cambia entre dos diagramas lo prueba ComparacionDeDiagramasTest.
 */
@ExtendWith(MockitoExtension.class)
class ComparacionServiceTest {

    private static final Long EMPRESA = 7L;

    @Mock
    private DiagramaService diagramaService;
    @Mock
    private VersionService versionService;
    @Mock
    private ProcesoRepository procesoRepository;
    @Mock
    private ProcesoService procesoService;
    @Mock
    private AplicacionDelModelo aplicacion;
    @Mock
    private ArmadoDelDiagrama armado;
    @Mock
    private PlatformTransactionManager transacciones;

    private final JsonMapper json = JsonMapper.builder().build();
    private ComparacionServiceImpl service;
    private Proceso proceso;
    private DiagramaResponse demo;

    @BeforeEach
    void setUp() {
        service = new ComparacionServiceImpl(diagramaService, versionService, procesoRepository, procesoService,
                aplicacion, armado, json, transacciones);
        proceso = Proceso.builder().id(10L).build();
        demo = DiagramaArmado.demo().diagrama();
    }

    @Test
    @DisplayName("El borrador contra si mismo no tiene diferencias, y se lee del modelo vivo")
    void borradorContraBorrador() {
        conProceso();
        when(diagramaService.obtener(EMPRESA, 10L)).thenReturn(demo);

        ComparacionResponse comparacion = service.comparar(EMPRESA, 10L, "borrador", "borrador");

        assertThat(comparacion.de()).isEqualTo("borrador");
        assertThat(comparacion.agregados()).isEmpty();
        assertThat(comparacion.quitados()).isEmpty();
        assertThat(comparacion.cambiados()).isEmpty();
        verify(versionService, never()).definicion(anyLong(), anyLong(), anyInt());
    }

    @Test
    @DisplayName("Vigente es la version publicada, y un numero es esa version, leida de su instantanea")
    void vigenteYNumero() {
        proceso.setVersionPublicada(3);
        conProceso();
        String instantanea = json.writeValueAsString(demo);
        when(versionService.definicion(EMPRESA, 10L, 3)).thenReturn(instantanea);
        when(versionService.definicion(EMPRESA, 10L, 2)).thenReturn(instantanea);

        ComparacionResponse comparacion = service.comparar(EMPRESA, 10L, "2", "vigente");

        assertThat(comparacion.de()).isEqualTo("2");
        assertThat(comparacion.a()).isEqualTo("vigente");
        assertThat(comparacion.agregados()).isEmpty();
        assertThat(comparacion.quitados()).isEmpty();
        verify(versionService).definicion(EMPRESA, 10L, 2);
        verify(versionService).definicion(EMPRESA, 10L, 3);
        verify(diagramaService, never()).obtener(anyLong(), anyLong());
    }

    @Test
    @DisplayName("Sin version publicada no hay vigente, y un lado que no es numero ni palabra conocida es un 400")
    void ladosInvalidos() {
        conProceso();

        assertThatThrownBy(() -> service.comparar(EMPRESA, 10L, "vigente", "borrador"))
                .isInstanceOf(ReglaNegocioException.class).hasMessage("El proceso no tiene una versión vigente.");
        assertThatThrownBy(() -> service.comparar(EMPRESA, 10L, "ayer", "borrador"))
                .isInstanceOf(SolicitudInvalidaException.class)
                .hasMessage("Se compara una versión por su número, vigente o borrador.");
    }

    @Test
    @DisplayName("Un proceso que no esta activo en la tienda no se compara")
    void procesoAjeno() {
        assertThatThrownBy(() -> service.comparar(EMPRESA, 10L, "borrador", "vigente"))
                .isInstanceOf(RecursoNoEncontradoException.class).hasMessage("Proceso no encontrado.");
    }

    private void conProceso() {
        when(procesoRepository.findByIdAndEmpresaIdAndActivoTrue(10L, EMPRESA)).thenReturn(Optional.of(proceso));
    }
}
