package com.facimus.procesos.ejecucion.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

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

/** PR 71: el tablero trae el cumplimiento de los plazos, y el total de las tareas es la suma de cada proceso. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TableroServiceImplTest {

    private static final Long TIENDA = 1L;

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

    private TableroServiceImpl servicio;

    @BeforeEach
    void elServicio() {
        servicio = new TableroServiceImpl(casoRepository, actividadCasoRepository, mensajeSalienteRepository,
                mensajeEntranteRepository, eventoCasoRepository);
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
        given(actividadCasoRepository.cumplimientoPorProceso(TIENDA, null)).willReturn(porProceso);
        given(actividadCasoRepository.cumplimientoPorActividad(TIENDA, null)).willReturn(porActividad);
        given(actividadCasoRepository.cumplimientoPorPersona(TIENDA, null)).willReturn(porPersona);
        given(casoRepository.cumplimientoDeCasos(TIENDA, null)).willReturn(casos);

        CumplimientoResponse cumplimiento = servicio.de(TIENDA, null).cumplimiento();

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
        given(actividadCasoRepository.cumplimientoPorProceso(TIENDA, 10L)).willReturn(List.of());
        given(actividadCasoRepository.cumplimientoPorActividad(TIENDA, 10L)).willReturn(List.of());
        given(actividadCasoRepository.cumplimientoPorPersona(TIENDA, 10L)).willReturn(List.of());
        given(casoRepository.cumplimientoDeCasos(TIENDA, 10L)).willReturn(new CumplimientoDeCasosResponse(0, 0));

        CumplimientoResponse cumplimiento = servicio.de(TIENDA, 10L).cumplimiento();

        assertThat(cumplimiento.tareasATiempo()).isZero();
        assertThat(cumplimiento.tareasTarde()).isZero();
        assertThat(cumplimiento.casos()).isEqualTo(new CumplimientoDeCasosResponse(0, 0));
        assertThat(cumplimiento.porProceso()).isEmpty();
    }
}
