package com.facimus.procesos.ejecucion.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.model.RolAcceso;
import com.facimus.procesos.ejecucion.dto.response.CumplimientoDeCasosResponse;
import com.facimus.procesos.ejecucion.dto.response.CumplimientoPorActividadResponse;
import com.facimus.procesos.ejecucion.dto.response.CumplimientoPorPersonaResponse;
import com.facimus.procesos.ejecucion.dto.response.CumplimientoPorProcesoResponse;
import com.facimus.procesos.ejecucion.dto.response.CumplimientoResponse;
import com.facimus.procesos.ejecucion.repository.ActividadCasoRepository;
import com.facimus.procesos.ejecucion.repository.CasoRepository;
import com.facimus.procesos.ejecucion.repository.EventoCasoRepository;
import com.facimus.procesos.ejecucion.repository.MensajeEntranteRepository;
import com.facimus.procesos.ejecucion.repository.MensajeSalienteRepository;
import com.facimus.procesos.ejecucion.repository.ProcesosVisibles;
import com.facimus.procesos.gestion.service.Actor;
import com.facimus.procesos.gestion.service.Permisos;
import com.facimus.procesos.gestion.service.ProcesoService;
import com.facimus.procesos.gestion.service.Visibilidad;

/**
 * PR 71: el tablero trae el cumplimiento de los plazos de lo que quien pregunta puede ver, el total de las tareas es la
 * suma de cada proceso, y un proceso de otra tienda es un 404.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TableroServiceImplTest {

    private static final Long TIENDA = 1L;
    private static final Actor QUIEN = new Actor(5L, TIENDA, RolAcceso.SOLO_LECTURA);
    private static final ProcesosVisibles TODOS = ProcesosVisibles.SIN_NADA_OCULTO;

    @Mock
    private CasoRepository casoRepository;
    @Mock
    private ActividadCasoRepository actividadCasoRepository;
    @Mock
    private MensajeSalienteRepository mensajeSalienteRepository;
    @Mock
    private MensajeEntranteRepository mensajeEntranteRepository;
    @Mock
    private EventoCasoRepository eventoCasoRepository;
    @Mock
    private ProcesoService procesoService;
    @Mock
    private Permisos permisos;

    private TableroServiceImpl servicio;

    @BeforeEach
    void elServicio() {
        servicio = new TableroServiceImpl(casoRepository, actividadCasoRepository, mensajeSalienteRepository,
                mensajeEntranteRepository, eventoCasoRepository, procesoService, permisos);
        given(permisos.visibilidad(QUIEN)).willReturn(Visibilidad.TODO);
        given(procesoService.existe(TIENDA, 10L)).willReturn(true);
        given(casoRepository.ticksDeCiclo(any(), any())).willReturn(List.of());
        given(eventoCasoRepository.contarPorTipo(any(), any(), any())).willReturn(List.of());
    }

    @Test
    @DisplayName("Las tareas a tiempo y tarde son la suma de cada proceso; casos, actividades y personas tal cual")
    void cumplimiento() {
        List<CumplimientoPorProcesoResponse> porProceso = List.of(
                new CumplimientoPorProcesoResponse(10L, "Refunds", 7, 2),
                new CumplimientoPorProcesoResponse(11L, "Returns", 3, 5));
        List<CumplimientoPorActividadResponse> porActividad = List.of(
                new CumplimientoPorActividadResponse(10L, "Refunds", 12L, "Approve", 7, 2));
        List<CumplimientoPorPersonaResponse> porPersona = List.of(
                new CumplimientoPorPersonaResponse(5L, "Ana", 4, 1));
        CumplimientoDeCasosResponse casos = new CumplimientoDeCasosResponse(6, 1);
        given(actividadCasoRepository.cumplimientoPorProceso(TIENDA, null, TODOS)).willReturn(porProceso);
        given(actividadCasoRepository.cumplimientoPorActividad(TIENDA, null, TODOS)).willReturn(porActividad);
        given(actividadCasoRepository.cumplimientoPorPersona(TIENDA, null, TODOS)).willReturn(porPersona);
        given(casoRepository.cumplimientoDeCasos(TIENDA, null, TODOS)).willReturn(casos);

        CumplimientoResponse cumplimiento = servicio.de(QUIEN, null).cumplimiento();

        assertThat(cumplimiento.tareasATiempo()).isEqualTo(10);
        assertThat(cumplimiento.tareasTarde()).isEqualTo(7);
        assertThat(cumplimiento.casos()).isEqualTo(casos);
        assertThat(cumplimiento.porProceso()).isEqualTo(porProceso);
        assertThat(cumplimiento.porActividad()).isEqualTo(porActividad);
        assertThat(cumplimiento.porPersona()).isEqualTo(porPersona);
    }

    @Test
    @DisplayName("El de un proceso pregunta solo por el, y sin plazos todo es cero")
    void deUnProceso() {
        given(actividadCasoRepository.cumplimientoPorProceso(TIENDA, 10L, TODOS)).willReturn(List.of());
        given(actividadCasoRepository.cumplimientoPorActividad(TIENDA, 10L, TODOS)).willReturn(List.of());
        given(actividadCasoRepository.cumplimientoPorPersona(TIENDA, 10L, TODOS)).willReturn(List.of());
        given(casoRepository.cumplimientoDeCasos(TIENDA, 10L, TODOS)).willReturn(new CumplimientoDeCasosResponse(0, 0));

        CumplimientoResponse cumplimiento = servicio.de(QUIEN, 10L).cumplimiento();

        assertThat(cumplimiento.tareasATiempo()).isZero();
        assertThat(cumplimiento.tareasTarde()).isZero();
        assertThat(cumplimiento.casos()).isEqualTo(new CumplimientoDeCasosResponse(0, 0));
        assertThat(cumplimiento.porProceso()).isEmpty();
    }

    @Test
    @DisplayName("Un proceso que no es de la tienda es un 404, y no se cuenta nada")
    void procesoDeOtraTienda() {
        given(procesoService.existe(TIENDA, 99L)).willReturn(false);

        assertThatThrownBy(() -> servicio.de(QUIEN, 99L)).isInstanceOf(RecursoNoEncontradoException.class);
        verifyNoInteractions(casoRepository, actividadCasoRepository);
    }

    @Test
    @DisplayName("El cumplimiento se pide con lo que quien pregunta puede ver, y el tablero de la tienda no pregunta "
            + "si existe un proceso")
    void conLoQueSeVe() {
        given(permisos.visibilidad(QUIEN)).willReturn(new Visibilidad(false, Set.of(3L), Set.of(11L), Set.of(12L)));
        ProcesosVisibles visibles = new ProcesosVisibles(false, Set.of(3L), Set.of(11L), Set.of(12L));
        given(actividadCasoRepository.cumplimientoPorProceso(TIENDA, null, visibles)).willReturn(List.of(
                new CumplimientoPorProcesoResponse(10L, "Refunds", 1, 1)));
        given(casoRepository.cumplimientoDeCasos(TIENDA, null, visibles)).willReturn(new CumplimientoDeCasosResponse(
                4, 0));

        CumplimientoResponse cumplimiento = servicio.de(QUIEN, null).cumplimiento();

        assertThat(cumplimiento.tareasATiempo()).isOne();
        assertThat(cumplimiento.casos()).isEqualTo(new CumplimientoDeCasosResponse(4, 0));
        verify(actividadCasoRepository).cumplimientoPorActividad(TIENDA, null, visibles);
        verify(actividadCasoRepository).cumplimientoPorPersona(TIENDA, null, visibles);
        verify(procesoService, never()).existe(any(), any());
    }
}
