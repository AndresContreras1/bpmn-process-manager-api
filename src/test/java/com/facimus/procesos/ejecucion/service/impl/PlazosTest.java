package com.facimus.procesos.ejecucion.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.facimus.procesos.common.model.Empresa;
import com.facimus.procesos.ejecucion.model.ActividadCaso;
import com.facimus.procesos.ejecucion.model.Caso;
import com.facimus.procesos.ejecucion.model.EstadoPlazo;
import com.facimus.procesos.ejecucion.model.ModoCaso;
import com.facimus.procesos.ejecucion.model.TipoEventoCaso;
import com.facimus.procesos.ejecucion.model.TipoNodoCaso;
import com.facimus.procesos.ejecucion.model.Vencimiento;
import com.facimus.procesos.ejecucion.repository.ActividadCasoRepository;
import com.facimus.procesos.gestion.model.Pais;
import com.facimus.procesos.gestion.service.CalendarioLaboral;
import com.facimus.procesos.gestion.service.CalendarioService;
import com.facimus.procesos.modelado.dto.response.PlazoResponse;
import com.facimus.procesos.modelado.model.UnidadDePlazo;

/** PR 71: el motor arma el plazo de una tarea y de un caso, y lo cierra al completarla o al terminarlo. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PlazosTest {

    private static final Long TIENDA = 1L;
    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    /** Viernes 9 de enero de 2026 a las tres de la tarde en Bogota. */
    private static final Instant VIERNES = Instant.parse("2026-01-09T20:00:00Z");
    private static final int TICK = 3;
    private static final CalendarioLaboral COLOMBIA = new CalendarioLaboral(Pais.CO, BOGOTA, LocalTime.of(8, 0),
            LocalTime.of(17, 0), EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), Map.of(), Set.of());

    @Mock
    private CalendarioService calendarioService;
    @Mock
    private ActividadCasoRepository actividadCasoRepository;
    @Mock
    private Bitacora bitacora;

    private final GrafoDeVersion grafo = mock(GrafoDeVersion.class);
    private Plazos plazos;
    private Caso caso;
    private ActividadCaso tarea;

    @BeforeEach
    void unCaso() {
        plazos = new Plazos(calendarioService, actividadCasoRepository, bitacora, Clock.fixed(VIERNES,
                ZoneOffset.UTC));
        given(calendarioService.laboral(TIENDA)).willReturn(COLOMBIA);
        caso = Caso.builder().id(42L).empresa(Empresa.builder().id(TIENDA).build()).modo(ModoCaso.PRODUCCION)
                .build();
        tarea = ActividadCaso.builder().id(77L).caso(caso).nodoId(12L).nodoNombre("Approve the refund")
                .tipoNodo(TipoNodoCaso.ACTIVIDAD).subtipo("USUARIO").build();
        given(grafo.plazoDe(any())).willReturn(Optional.empty());
    }

    @Test
    @DisplayName("Una tarea con plazo en produccion vence con el calendario, se guarda y la linea de tiempo lo dice")
    void tarea_enProduccion() {
        given(grafo.plazoDe(12L)).willReturn(Optional.of(new PlazoResponse(2, UnidadDePlazo.DIAS_HABILES)));

        plazos.armarDeLaTarea(caso, grafo, tarea, new Momento(TICK, null));

        assertThat(tarea.getVencimiento().getVenceEn()).isEqualTo(LocalDateTime.parse("2026-01-14T20:00:00"));
        assertThat(tarea.getVencimiento().getAvisoEn()).isEqualTo(LocalDateTime.parse("2026-01-14T15:30:00"));
        assertThat(tarea.getVencimiento().getEstado()).isEqualTo(EstadoPlazo.A_TIEMPO);
        verify(actividadCasoRepository).save(tarea);
        verify(bitacora).anotar(caso, TICK, TipoEventoCaso.TAREA_CREADA,
                "\"Approve the refund\" vence el 2026-01-14 15:00 (America/Bogota).");
    }

    @Test
    @DisplayName("En prueba vence en ticks, contados desde el tick de la operacion")
    void tarea_enPrueba() {
        caso.setModo(ModoCaso.PRUEBA);
        given(grafo.plazoDe(12L)).willReturn(Optional.of(new PlazoResponse(8, UnidadDePlazo.HORAS)));

        plazos.armarDeLaTarea(caso, grafo, tarea, new Momento(TICK, null));

        assertThat(tarea.getVencimiento().getVenceTick()).isEqualTo(11);
        assertThat(tarea.getVencimiento().getAvisoTick()).isEqualTo(9);
        assertThat(tarea.getVencimiento().getVenceEn()).isNull();
        verify(bitacora).anotar(caso, TICK, TipoEventoCaso.TAREA_CREADA,
                "\"Approve the refund\" vence en el tick 11.");
    }

    @Test
    @DisplayName("Una tarea sin plazo en la version no arma nada ni escribe nada")
    void tarea_sinPlazo() {
        plazos.armarDeLaTarea(caso, grafo, tarea, new Momento(TICK, null));

        assertThat(tarea.getVencimiento()).isNull();
        verifyNoInteractions(actividadCasoRepository, bitacora, calendarioService);
    }

    @Test
    @DisplayName("Un calendario que no deja contar el plazo no lo arma, y la linea de tiempo lo dice")
    void tarea_calendarioQueNoCuenta() {
        CalendarioLaboral sinTiempo = mock(CalendarioLaboral.class);
        given(sinTiempo.jornada()).willReturn(Duration.ofHours(9));
        given(sinTiempo.sumarHoras(any(), any())).willThrow(new IllegalStateException("Sin tiempo habil."));
        given(calendarioService.laboral(TIENDA)).willReturn(sinTiempo);
        given(grafo.plazoDe(12L)).willReturn(Optional.of(new PlazoResponse(2, UnidadDePlazo.DIAS_HABILES)));

        plazos.armarDeLaTarea(caso, grafo, tarea, new Momento(TICK, null));

        assertThat(tarea.getVencimiento()).isNull();
        verify(actividadCasoRepository, never()).save(any());
        verify(bitacora).anotar(caso, TICK, TipoEventoCaso.NODO_ACTIVADO,
                "El plazo no se arma: el calendario de la tienda no tiene tiempo habil para contarlo.");
    }

    @Test
    @DisplayName("El caso arma el plazo de su inicio, y la linea de tiempo lo dice")
    void caso_conPlazo() {
        NodoDeLaVersion inicio = new NodoDeLaVersion(40L, "Refund requested", TipoNodoCaso.EVENTO, "INICIO", 7L,
                null);
        given(grafo.plazoDe(40L)).willReturn(Optional.of(new PlazoResponse(24, UnidadDePlazo.HORAS)));

        plazos.armarDelCaso(caso, grafo, inicio, new Momento(TICK, 5L));

        assertThat(caso.getVencimiento().getVenceEn()).isEqualTo(LocalDateTime.parse("2026-01-10T20:00:00"));
        assertThat(caso.getVencimiento().getAvisoEn()).isEqualTo(LocalDateTime.parse("2026-01-10T14:00:00"));
        verify(bitacora).anotar(caso, TICK, TipoEventoCaso.CASO_ABIERTO,
                "El caso vence el 2026-01-10 15:00 (America/Bogota).");
    }

    @Test
    @DisplayName("Un inicio sin plazo deja el caso sin plazo")
    void caso_sinPlazo() {
        NodoDeLaVersion inicio = new NodoDeLaVersion(40L, "Refund requested", TipoNodoCaso.EVENTO, "INICIO", 7L,
                null);

        plazos.armarDelCaso(caso, grafo, inicio, new Momento(TICK, 5L));

        assertThat(caso.getVencimiento()).isNull();
        verifyNoInteractions(bitacora);
    }

    @Test
    @DisplayName("Completar despues de vencer la deja vencida aunque el repaso no hubiera pasado")
    void completar_tarde() {
        tarea.setVencimiento(Vencimiento.enProduccion(LocalDateTime.parse("2026-01-09T18:00:00"),
                LocalDateTime.parse("2026-01-09T20:00:00")));

        plazos.alCompletar(tarea, new Momento(TICK, 5L));

        assertThat(tarea.getVencimiento().getEstado()).isEqualTo(EstadoPlazo.VENCIDA);
    }

    @Test
    @DisplayName("Completar antes de vencer la deja como iba: a tiempo, o por vencer")
    void completar_aTiempo() {
        tarea.setVencimiento(Vencimiento.enProduccion(LocalDateTime.parse("2026-01-09T21:00:00"),
                LocalDateTime.parse("2026-01-09T22:00:00")));
        plazos.alCompletar(tarea, new Momento(TICK, 5L));
        assertThat(tarea.getVencimiento().getEstado()).isEqualTo(EstadoPlazo.A_TIEMPO);

        tarea.setVencimiento(Vencimiento.enProduccion(LocalDateTime.parse("2026-01-09T19:00:00"),
                LocalDateTime.parse("2026-01-09T20:00:01")));
        plazos.alCompletar(tarea, new Momento(TICK, 5L));
        assertThat(tarea.getVencimiento().getEstado()).isEqualTo(EstadoPlazo.POR_VENCER);
    }

    @Test
    @DisplayName("En prueba se cierra con el tick de la operacion, no con el reloj de pared")
    void completar_enPrueba() {
        tarea.setVencimiento(Vencimiento.enPrueba(5, 6));
        plazos.alCompletar(tarea, new Momento(5, 5L));
        assertThat(tarea.getVencimiento().getEstado()).isEqualTo(EstadoPlazo.POR_VENCER);

        tarea.setVencimiento(Vencimiento.enPrueba(5, 6));
        plazos.alCompletar(tarea, new Momento(6, 5L));
        assertThat(tarea.getVencimiento().getEstado()).isEqualTo(EstadoPlazo.VENCIDA);
    }

    @Test
    @DisplayName("Un caso que termina tarde queda vencido; sin plazo no pasa nada")
    void terminar() {
        plazos.alTerminar(caso, new Momento(TICK, null));
        assertThat(caso.getVencimiento()).isNull();
        plazos.alCompletar(tarea, new Momento(TICK, null));
        assertThat(tarea.getVencimiento()).isNull();

        caso.setVencimiento(Vencimiento.enProduccion(LocalDateTime.parse("2026-01-09T10:00:00"),
                LocalDateTime.parse("2026-01-09T12:00:00")));
        plazos.alTerminar(caso, new Momento(TICK, null));

        assertThat(caso.getVencimiento().getEstado()).isEqualTo(EstadoPlazo.VENCIDA);
        verify(bitacora, never()).anotar(any(), anyInt(), any(), anyString());
    }
}
