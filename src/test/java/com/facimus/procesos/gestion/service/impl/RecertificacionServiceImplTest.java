package com.facimus.procesos.gestion.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;

import com.facimus.procesos.common.notificaciones.NotificacionPedida;
import com.facimus.procesos.common.notificaciones.TipoDeNotificacion;
import com.facimus.procesos.common.security.ContextoDeTienda;
import com.facimus.procesos.common.security.ContextoDeTienda.Alcance;
import com.facimus.procesos.common.trabajos.ColaDeTrabajos;
import com.facimus.procesos.common.trabajos.Trabajo;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.repository.ProcesoRepository;
import com.facimus.procesos.gestion.service.RecertificacionService;

/** PR 64: los avisos de la recertificacion, una vez por fecha de revision y por aviso. */
@ExtendWith(MockitoExtension.class)
class RecertificacionServiceImplTest {

    private static final Long EMPRESA = 1L;
    private static final Long DUENA = 4L;
    private static final LocalDate HOY = LocalDate.of(2027, 3, 1);

    @Mock
    private ProcesoRepository procesoRepository;
    @Mock
    private ColaDeTrabajos cola;
    @Mock
    private ApplicationEventPublisher eventos;

    @InjectMocks
    private RecertificacionServiceImpl servicio;

    private static Proceso proceso(Long id, LocalDate revision, LocalDate avisada, LocalDate vencidaAvisada) {
        return Proceso.builder().id(id).duenoId(DUENA).proximaRevision(revision).revisionAvisada(avisada)
                .vencidaAvisada(vencidaAvisada).build();
    }

    private void pendientes(Proceso... procesos) {
        when(procesoRepository.revisionesPorAvisar(EMPRESA, HOY)).thenReturn(List.of(procesos));
    }

    @Test
    @DisplayName("el reloj diario deja un trabajo con el dia por cada tienda con algo que avisar")
    void encolar() {
        when(procesoRepository.tiendasConRevisionesPorAvisar(HOY)).thenReturn(List.of(1L, 2L));

        assertThat(servicio.encolarAvisos(HOY)).isEqualTo(2);

        verify(cola).encolar(1L, RecertificacionServiceImpl.TRABAJO, "2027-03-01");
        verify(cola).encolar(2L, RecertificacionServiceImpl.TRABAJO, "2027-03-01");
    }

    @Test
    @DisplayName("el dia de la revision se avisa a la duena que llego, y se anota por esa fecha")
    void llego() {
        pendientes(proceso(10L, HOY, null, LocalDate.of(2026, 1, 1)));

        assertThat(servicio.avisar(EMPRESA, HOY)).isOne();

        verify(eventos).publishEvent(new NotificacionPedida(EMPRESA, TipoDeNotificacion.RECERTIFICACION_PENDIENTE,
                null, 10L, null, List.of(DUENA)));
        // El aviso de vencida de otra fecha se queda como estaba
        verify(procesoRepository).anotarAvisos(10L, EMPRESA, HOY, LocalDate.of(2026, 1, 1));
    }

    @Test
    @DisplayName("pasada la fecha se avisa una vez mas que vencio, y quedan anotados los dos avisos")
    void vencio() {
        LocalDate ayer = HOY.minusDays(1);
        pendientes(proceso(10L, ayer, ayer, null));

        assertThat(servicio.avisar(EMPRESA, HOY)).isOne();

        verify(eventos).publishEvent(new NotificacionPedida(EMPRESA, TipoDeNotificacion.RECERTIFICACION_VENCIDA,
                null, 10L, null, List.of(DUENA)));
        verify(procesoRepository).anotarAvisos(10L, EMPRESA, ayer, ayer);
    }

    @Test
    @DisplayName("una fecha que ya llega vencida recibe solo el aviso de vencida")
    void llegaVencida() {
        LocalDate antes = HOY.minusMonths(2);
        pendientes(proceso(10L, antes, null, null));

        assertThat(servicio.avisar(EMPRESA, HOY)).isOne();

        verify(eventos).publishEvent(new NotificacionPedida(EMPRESA, TipoDeNotificacion.RECERTIFICACION_VENCIDA,
                null, 10L, null, List.of(DUENA)));
        verify(eventos, never()).publishEvent(new NotificacionPedida(EMPRESA,
                TipoDeNotificacion.RECERTIFICACION_PENDIENTE, null, 10L, null, List.of(DUENA)));
        verify(procesoRepository).anotarAvisos(10L, EMPRESA, antes, antes);
    }

    @Test
    @DisplayName("lo ya avisado por esa fecha no se vuelve a avisar: un trabajo repetido no repite el correo")
    void sinRepetir() {
        LocalDate ayer = HOY.minusDays(1);
        pendientes(proceso(10L, HOY, HOY, null), proceso(11L, ayer, ayer, ayer));

        assertThat(servicio.avisar(EMPRESA, HOY)).isZero();

        verify(eventos, never()).publishEvent(any());
        verify(procesoRepository, never()).anotarAvisos(anyLong(), any(), any(), any());
    }

    @Test
    @DisplayName("cuenta cada aviso pedido, y corre en la tienda del trabajo y deja el alcance de antes")
    void enSuTienda() {
        AtomicReference<Optional<Alcance>> durante = new AtomicReference<>();
        when(procesoRepository.revisionesPorAvisar(EMPRESA, HOY)).thenAnswer(llamada -> {
            durante.set(ContextoDeTienda.actual());
            return List.of(proceso(10L, HOY, null, null), proceso(11L, HOY.minusDays(3), HOY.minusDays(3), null));
        });
        Optional<Alcance> antes = ContextoDeTienda.actual();

        assertThat(servicio.avisar(EMPRESA, HOY)).isEqualTo(2);

        assertThat(durante.get()).contains(Alcance.deLaTienda(EMPRESA));
        assertThat(ContextoDeTienda.actual()).isEqualTo(antes);
    }

    @Test
    @DisplayName("el trabajo de la cola avisa el dia que lleva, no el de hoy")
    @SuppressWarnings("unchecked")
    void manejador() {
        RecertificacionService recertificacion = mock(RecertificacionService.class);
        ObjectProvider<RecertificacionService> proveedor = mock(ObjectProvider.class);
        when(proveedor.getObject()).thenReturn(recertificacion);
        AvisosDeRecertificacion manejador = new AvisosDeRecertificacion(proveedor);

        manejador.ejecutar(new Trabajo(1L, EMPRESA, RecertificacionServiceImpl.TRABAJO, "2027-03-01", 1, 5));

        assertThat(manejador.tipo()).isEqualTo("recertificacion.avisos");
        verify(recertificacion).avisar(EMPRESA, HOY);
    }
}
