package com.facimus.procesos.ejecucion.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.facimus.procesos.common.model.Empresa;
import com.facimus.procesos.common.notificaciones.TipoDeNotificacion;
import com.facimus.procesos.ejecucion.model.ActividadCaso;
import com.facimus.procesos.ejecucion.model.Caso;
import com.facimus.procesos.ejecucion.model.EstadoActividadCaso;
import com.facimus.procesos.ejecucion.model.EstadoCaso;
import com.facimus.procesos.ejecucion.model.EstadoPlazo;
import com.facimus.procesos.ejecucion.model.TipoEventoCaso;
import com.facimus.procesos.ejecucion.model.Vencimiento;
import com.facimus.procesos.ejecucion.repository.ActividadCasoRepository;
import com.facimus.procesos.ejecucion.repository.CasoRepository;

/** PR 71: mover un plazo una sola vez, con su linea de tiempo y su aviso, o no hacer nada. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RevisionDePlazosTest {

    private static final Long TIENDA = 1L;
    private static final int TICK_DE_LA_TIENDA = 12;
    /** El aviso a las tres y media UTC, el vencimiento a las ocho. */
    private static final LocalDateTime AVISO = LocalDateTime.parse("2026-01-14T15:30:00");
    private static final LocalDateTime VENCE = LocalDateTime.parse("2026-01-14T20:00:00");

    @Mock
    private ActividadCasoRepository actividadCasoRepository;
    @Mock
    private CasoRepository casoRepository;
    @Mock
    private Bitacora bitacora;
    @Mock
    private AvisosDePlazo avisos;
    @Mock
    private RelojDeLaTienda relojDeLaTienda;

    private Caso caso;
    private ActividadCaso tarea;

    @BeforeEach
    void unaTareaQueEspera() {
        caso = Caso.builder().id(42L).empresa(Empresa.builder().id(TIENDA).build()).estado(EstadoCaso.ABIERTO)
                .build();
        tarea = ActividadCaso.builder().id(77L).caso(caso).nodoNombre("Approve the refund")
                .estado(EstadoActividadCaso.EN_ESPERA).vencimiento(Vencimiento.enProduccion(AVISO, VENCE)).build();
        given(actividadCasoRepository.findByIdAndEmpresaId(77L, TIENDA)).willReturn(Optional.of(tarea));
        given(casoRepository.findByIdAndEmpresaId(42L, TIENDA)).willReturn(Optional.of(caso));
        given(relojDeLaTienda.ahora(TIENDA)).willReturn(TICK_DE_LA_TIENDA);
        given(actividadCasoRepository.moverPlazo(anyLong(), anyLong(), anyString(), anyString())).willReturn(1);
        given(casoRepository.moverPlazo(anyLong(), anyLong(), anyString(), anyString())).willReturn(1);
    }

    @Nested
    @DisplayName("De una tarea")
    class DeLaTarea {

        @Test
        @DisplayName("Pasada la hora del aviso queda por vencer: una escritura condicionada, su linea y su aviso")
        void porVencer() {
            assertThat(a("2026-01-14T15:30:00Z").deLaTarea(TIENDA, 77L, null)).isTrue();

            verify(actividadCasoRepository).moverPlazo(77L, TIENDA, "A_TIEMPO", "POR_VENCER");
            verify(bitacora).anotar(caso, TICK_DE_LA_TIENDA, TipoEventoCaso.PLAZO_POR_VENCER,
                    "\"Approve the refund\" esta por vencer: entro en el ultimo cuarto de su plazo.");
            verify(avisos).deLaTarea(caso, tarea, TipoDeNotificacion.PLAZO_POR_VENCER);
        }

        @Test
        @DisplayName("Una por vencer, pasada la hora de vencer, queda vencida, con su aviso")
        void vencida() {
            tarea.getVencimiento().setEstado(EstadoPlazo.POR_VENCER);

            assertThat(a("2026-01-14T20:00:00Z").deLaTarea(TIENDA, 77L, null)).isTrue();

            verify(actividadCasoRepository).moverPlazo(77L, TIENDA, "POR_VENCER", "VENCIDA");
            verify(bitacora).anotar(caso, TICK_DE_LA_TIENDA, TipoEventoCaso.PLAZO_VENCIDO,
                    "\"Approve the refund\" paso su plazo.");
            verify(avisos).deLaTarea(caso, tarea, TipoDeNotificacion.PLAZO_VENCIDO);
        }

        @Test
        @DisplayName("Una a tiempo que ya vencio salta a vencida y solo avisa que vencio")
        void deUnSalto() {
            assertThat(a("2026-02-01T00:00:00Z").deLaTarea(TIENDA, 77L, null)).isTrue();

            verify(actividadCasoRepository).moverPlazo(77L, TIENDA, "A_TIEMPO", "VENCIDA");
            verify(avisos).deLaTarea(caso, tarea, TipoDeNotificacion.PLAZO_VENCIDO);
            verify(avisos, never()).deLaTarea(caso, tarea, TipoDeNotificacion.PLAZO_POR_VENCER);
        }

        @Test
        @DisplayName("Si no le toca cambiar, no se escribe nada")
        void sinCambio() {
            assertThat(a("2026-01-14T15:29:59Z").deLaTarea(TIENDA, 77L, null)).isFalse();

            verify(actividadCasoRepository, never()).moverPlazo(anyLong(), anyLong(), anyString(), anyString());
            verifyNoInteractions(bitacora, avisos);
        }

        @Test
        @DisplayName("Si otro repaso ya la movio, la escritura no toca ninguna fila y no se avisa otra vez")
        void otroYaLaMovio() {
            given(actividadCasoRepository.moverPlazo(77L, TIENDA, "A_TIEMPO", "POR_VENCER")).willReturn(0);

            assertThat(a("2026-01-14T16:00:00Z").deLaTarea(TIENDA, 77L, null)).isFalse();

            verifyNoInteractions(bitacora, avisos);
        }

        @Test
        @DisplayName("Una que ya no espera, sin plazo o de otra tienda no se toca")
        void noSeToca() {
            tarea.setEstado(EstadoActividadCaso.COMPLETADA);
            assertThat(a("2026-02-01T00:00:00Z").deLaTarea(TIENDA, 77L, null)).isFalse();
            tarea.setEstado(EstadoActividadCaso.EN_ESPERA);
            tarea.setVencimiento(null);
            assertThat(a("2026-02-01T00:00:00Z").deLaTarea(TIENDA, 77L, null)).isFalse();
            assertThat(a("2026-02-01T00:00:00Z").deLaTarea(2L, 77L, null)).isFalse();

            verify(actividadCasoRepository, never()).moverPlazo(anyLong(), anyLong(), anyString(), anyString());
            verifyNoInteractions(bitacora, avisos);
        }

        @Test
        @DisplayName("En prueba cuenta el tick que llega, no el reloj de pared, y la linea de tiempo va en ese tick")
        void enPrueba() {
            tarea.setVencimiento(Vencimiento.enPrueba(9, 11));

            assertThat(a("2099-01-01T00:00:00Z").deLaTarea(TIENDA, 77L, 8)).isFalse();
            assertThat(a("2000-01-01T00:00:00Z").deLaTarea(TIENDA, 77L, 9)).isTrue();

            verify(actividadCasoRepository).moverPlazo(77L, TIENDA, "A_TIEMPO", "POR_VENCER");
            verify(bitacora).anotar(any(), eq(9), eq(TipoEventoCaso.PLAZO_POR_VENCER), anyString());
            verify(relojDeLaTienda, never()).ahora(any());
        }
    }

    @Nested
    @DisplayName("Del caso")
    class DelCaso {

        @BeforeEach
        void conPlazo() {
            caso.setVencimiento(Vencimiento.enProduccion(AVISO, VENCE));
        }

        @Test
        @DisplayName("Por vencer y vencido, cada uno con su linea y su aviso del caso")
        void porVencerYVencido() {
            assertThat(a("2026-01-14T15:30:00Z").delCaso(TIENDA, 42L, null)).isTrue();
            verify(casoRepository).moverPlazo(42L, TIENDA, "A_TIEMPO", "POR_VENCER");
            verify(bitacora).anotar(caso, TICK_DE_LA_TIENDA, TipoEventoCaso.PLAZO_POR_VENCER,
                    "El caso esta por vencer: entro en el ultimo cuarto de su plazo.");
            verify(avisos).delCaso(caso, TipoDeNotificacion.PLAZO_POR_VENCER);

            caso.getVencimiento().setEstado(EstadoPlazo.POR_VENCER);
            assertThat(a("2026-01-14T20:00:00Z").delCaso(TIENDA, 42L, null)).isTrue();
            verify(casoRepository).moverPlazo(42L, TIENDA, "POR_VENCER", "VENCIDA");
            verify(bitacora).anotar(caso, TICK_DE_LA_TIENDA, TipoEventoCaso.PLAZO_VENCIDO, "El caso paso su plazo.");
            verify(avisos).delCaso(caso, TipoDeNotificacion.PLAZO_VENCIDO);
        }

        @Test
        @DisplayName("Uno en error sigue contando; uno cerrado o sin plazo no se toca")
        void cerradoOSinPlazo() {
            caso.setEstado(EstadoCaso.ERROR);
            assertThat(a("2026-01-14T20:00:00Z").delCaso(TIENDA, 42L, null)).isTrue();

            caso.getVencimiento().setEstado(EstadoPlazo.A_TIEMPO);
            caso.setEstado(EstadoCaso.TERMINADO);
            assertThat(a("2026-01-14T20:00:00Z").delCaso(TIENDA, 42L, null)).isFalse();
            caso.setEstado(EstadoCaso.ABIERTO);
            caso.setVencimiento(null);
            assertThat(a("2026-01-14T20:00:00Z").delCaso(TIENDA, 42L, null)).isFalse();
            assertThat(a("2026-01-14T20:00:00Z").delCaso(2L, 42L, null)).isFalse();
            verify(casoRepository).moverPlazo(anyLong(), anyLong(), anyString(), anyString());
        }

        @Test
        @DisplayName("Si otro repaso ya lo movio, no se avisa otra vez; si no le toca, no se escribe")
        void unaSolaVez() {
            given(casoRepository.moverPlazo(42L, TIENDA, "A_TIEMPO", "VENCIDA")).willReturn(0);
            assertThat(a("2026-01-14T20:00:00Z").delCaso(TIENDA, 42L, null)).isFalse();
            assertThat(a("2026-01-14T10:00:00Z").delCaso(TIENDA, 42L, null)).isFalse();

            verifyNoInteractions(bitacora, avisos);
        }
    }

    /** La revision con el reloj de pared parado en ese instante. */
    private RevisionDePlazos a(String instante) {
        return new RevisionDePlazos(actividadCasoRepository, casoRepository, bitacora, avisos, relojDeLaTienda,
                Clock.fixed(Instant.parse(instante), ZoneOffset.UTC));
    }
}
