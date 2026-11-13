package com.facimus.procesos.ejecucion.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import com.facimus.procesos.common.model.Empresa;
import com.facimus.procesos.common.notificaciones.NotificacionPedida;
import com.facimus.procesos.common.notificaciones.TipoDeNotificacion;
import com.facimus.procesos.ejecucion.model.ActividadCaso;
import com.facimus.procesos.ejecucion.model.Caso;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.service.MembresiaRolService;

/** PR 71: a quien llega cada aviso de un plazo o de un escalamiento, contado a mano. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AvisosDePlazoTest {

    private static final Long TIENDA = 1L;
    private static final Long ROL = 9L;
    private static final Long DUENO = 2L;
    private static final Long ANA = 5L;
    private static final Long LUIS = 6L;
    private static final Long INICIADOR = 8L;

    @Mock
    private ApplicationEventPublisher eventos;
    @Mock
    private MembresiaRolService membresiaRolService;

    private AvisosDePlazo avisos;
    private Proceso proceso;
    private Caso caso;
    private ActividadCaso tarea;

    @BeforeEach
    void unCaso() {
        avisos = new AvisosDePlazo(eventos, membresiaRolService);
        Empresa tienda = Empresa.builder().id(TIENDA).build();
        proceso = Proceso.builder().id(100L).empresa(tienda).nombre("Refunds").duenoId(DUENO).build();
        caso = Caso.builder().id(42L).empresa(tienda).proceso(proceso).iniciadoPor(INICIADOR).build();
        tarea = ActividadCaso.builder().id(77L).caso(caso).rolProcesoId(ROL).build();
        given(membresiaRolService.idsDeLosMiembrosDe(TIENDA, ROL)).willReturn(List.of(ANA, LUIS, DUENO));
    }

    @Test
    @DisplayName("La tarea que alguien tiene: a esa persona y al dueno, una notificacion que senala el caso")
    void tarea_conQuienLaTiene() {
        tarea.setAsignadoA(ANA);

        avisos.deLaTarea(caso, tarea, TipoDeNotificacion.PLAZO_POR_VENCER);

        assertThat(publicada()).isEqualTo(new NotificacionPedida(TIENDA, TipoDeNotificacion.PLAZO_POR_VENCER, null,
                100L, 42L, List.of(ANA, DUENO)));
        verifyNoInteractions(membresiaRolService);
    }

    @Test
    @DisplayName("La que nadie tiene: a quienes pertenecen a su rol y al dueno, sin repetir a nadie")
    void tarea_enLaBandejaDelRol() {
        avisos.deLaTarea(caso, tarea, TipoDeNotificacion.PLAZO_VENCIDO);

        assertThat(publicada().destinatarios()).containsExactly(ANA, LUIS, DUENO);
        assertThat(publicada().tipo()).isEqualTo(TipoDeNotificacion.PLAZO_VENCIDO);
    }

    @Test
    @DisplayName("Sin rol y sin dueno no hay a quien avisar, y no se publica nada")
    void tarea_sinNadie() {
        tarea.setRolProcesoId(null);
        proceso.setDuenoId(null);

        avisos.deLaTarea(caso, tarea, TipoDeNotificacion.PLAZO_VENCIDO);

        verify(eventos, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("Sin dueno, solo a quien la tiene")
    void tarea_sinDueno() {
        tarea.setAsignadoA(ANA);
        proceso.setDuenoId(null);

        avisos.deLaTarea(caso, tarea, TipoDeNotificacion.PLAZO_VENCIDO);

        assertThat(publicada().destinatarios()).containsExactly(ANA);
    }

    @Test
    @DisplayName("El caso: a quien lo abrio y al dueno; uno que abrio un mensaje, solo al dueno")
    void caso() {
        avisos.delCaso(caso, TipoDeNotificacion.PLAZO_POR_VENCER);
        assertThat(publicada()).isEqualTo(new NotificacionPedida(TIENDA, TipoDeNotificacion.PLAZO_POR_VENCER, null,
                100L, 42L, List.of(INICIADOR, DUENO)));

        caso.setIniciadoPor(null);
        avisos.delCaso(caso, TipoDeNotificacion.PLAZO_VENCIDO);
        assertThat(publicada().destinatarios()).containsExactly(DUENO);
    }

    @Test
    @DisplayName("Un escalamiento: a quien la tenia y al supervisor, sin nulos ni repetidos")
    void escalamiento() {
        avisos.deUnEscalamiento(caso, Arrays.asList(null, LUIS, LUIS));

        assertThat(publicada()).isEqualTo(new NotificacionPedida(TIENDA, TipoDeNotificacion.TAREA_ESCALADA, null,
                100L, 42L, List.of(LUIS)));
    }

    private NotificacionPedida publicada() {
        ArgumentCaptor<NotificacionPedida> pedido = ArgumentCaptor.forClass(NotificacionPedida.class);
        verify(eventos, atLeastOnce()).publishEvent(pedido.capture());
        return pedido.getValue();
    }
}
