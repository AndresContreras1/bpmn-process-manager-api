package com.facimus.procesos.gestion.service.impl;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.facimus.procesos.common.notificaciones.NotificacionPedida;
import com.facimus.procesos.common.notificaciones.TipoDeNotificacion;
import com.facimus.procesos.gestion.service.NotificacionService;

/** PR 63: lo que piden los demas modulos llega tal cual al centro de notificaciones. */
class NotificacionListenerTest {

    @Test
    @DisplayName("Cada pedido que sale del outbox se convierte en notificaciones")
    void notificar() {
        NotificacionService servicio = mock(NotificacionService.class);
        NotificacionPedida pedido = new NotificacionPedida(1L, TipoDeNotificacion.MENCION, 2L, 3L, 12L, List.of(5L));

        new NotificacionListener(servicio).notificar(pedido);

        verify(servicio).notificar(pedido);
    }
}
