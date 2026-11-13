package com.facimus.procesos.ejecucion.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageRequest;

import com.facimus.procesos.common.security.ContextoDeTienda;
import com.facimus.procesos.ejecucion.repository.ActividadCasoRepository;
import com.facimus.procesos.ejecucion.repository.CasoRepository;
import com.facimus.procesos.ejecucion.repository.PlazoQueCambia;

/** PR 71: el repaso de los plazos mueve cada uno en su tienda, cuenta los que cambiaron y no se frena por uno que falla. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PlazoServiceImplTest {

    private static final Instant AHORA = Instant.parse("2026-01-14T20:00:00Z");

    @Mock
    private ActividadCasoRepository actividadCasoRepository;
    @Mock
    private CasoRepository casoRepository;
    @Mock
    private RevisionDePlazos revision;

    private PlazoServiceImpl servicio;

    @BeforeEach
    void elServicio() {
        servicio = new PlazoServiceImpl(actividadCasoRepository, casoRepository, revision,
                Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("En produccion busca con la hora de pared, en lotes de 100, y mueve cada uno con el alcance de su tienda")
    void enProduccion() {
        given(actividadCasoRepository.plazosQueCambianEnProduccion(LocalDateTime.parse("2026-01-14T20:00:00"),
                PageRequest.of(0, PlazoServiceImpl.LOTE))).willReturn(List.of(new PlazoQueCambia(77L, 1L),
                        new PlazoQueCambia(78L, 2L)));
        given(casoRepository.plazosQueCambianEnProduccion(LocalDateTime.parse("2026-01-14T20:00:00"),
                PageRequest.of(0, PlazoServiceImpl.LOTE))).willReturn(List.of(new PlazoQueCambia(42L, 1L)));
        List<Long> tiendas = new ArrayList<>();
        given(revision.deLaTarea(any(), any(), any())).willAnswer(llamada -> {
            tiendas.add(ContextoDeTienda.actual().orElseThrow().empresaId());
            return !Long.valueOf(78L).equals(llamada.getArgument(1));
        });
        given(revision.delCaso(1L, 42L, null)).willAnswer(llamada -> {
            tiendas.add(ContextoDeTienda.actual().orElseThrow().empresaId());
            return true;
        });

        assertThat(servicio.revisarVencidos()).isEqualTo(2);

        verify(revision).deLaTarea(1L, 77L, null);
        verify(revision).deLaTarea(2L, 78L, null);
        verify(revision).delCaso(1L, 42L, null);
        assertThat(tiendas).containsExactly(1L, 2L, 1L);
    }

    @Test
    @DisplayName("Uno que falla no frena a los demas: queda pendiente para la pasada siguiente")
    void unoQueFalla() {
        given(actividadCasoRepository.plazosQueCambianEnProduccion(any(), any())).willReturn(List.of(
                new PlazoQueCambia(77L, 1L), new PlazoQueCambia(78L, 1L)));
        given(casoRepository.plazosQueCambianEnProduccion(any(), any())).willReturn(List.of(new PlazoQueCambia(42L,
                1L)));
        given(revision.deLaTarea(1L, 77L, null)).willThrow(new IllegalStateException("se cayo la base"));
        given(revision.deLaTarea(1L, 78L, null)).willReturn(true);
        given(revision.delCaso(1L, 42L, null)).willThrow(new IllegalStateException("otra vez"));

        assertThat(servicio.revisarVencidos()).isOne();
        verify(revision).deLaTarea(1L, 78L, null);
    }

    @Test
    @DisplayName("En prueba busca los de la tienda en ese tick y los mueve con ese tick")
    void enPrueba() {
        given(actividadCasoRepository.plazosQueCambianEnPrueba(1L, 9)).willReturn(List.of(new PlazoQueCambia(77L,
                1L)));
        given(casoRepository.plazosQueCambianEnPrueba(1L, 9)).willReturn(List.of(new PlazoQueCambia(42L, 1L),
                new PlazoQueCambia(43L, 1L)));
        given(revision.deLaTarea(1L, 77L, 9)).willReturn(true);
        given(revision.delCaso(1L, 42L, 9)).willReturn(true);
        given(revision.delCaso(1L, 43L, 9)).willThrow(new IllegalStateException("no"));

        assertThat(servicio.revisarEnTick(1L, 9)).isEqualTo(2);
    }

    @Test
    @DisplayName("Sin nada que mover no se toca nada")
    void sinNada() {
        given(actividadCasoRepository.plazosQueCambianEnProduccion(any(), any())).willReturn(List.of());
        given(casoRepository.plazosQueCambianEnProduccion(any(), any())).willReturn(List.of());

        assertThat(servicio.revisarVencidos()).isZero();
        assertThat(servicio.revisarEnTick(1L, 3)).isZero();
        verifyNoInteractions(revision);
    }
}
