package com.facimus.procesos.gestion.service.impl;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import com.facimus.procesos.common.notificaciones.NotificacionPedida;
import com.facimus.procesos.gestion.service.NotificacionService;

/**
 * PR 63: las notificaciones se crean con lo que piden los demas modulos; ninguno las escribe a mano. Como la bitacora:
 * despues del commit de lo que paso, en su propia transaccion, y el pedido espera en el outbox hasta que queda hecho.
 */
@Component
public class NotificacionListener {

    private final NotificacionService notificacionService;

    public NotificacionListener(NotificacionService notificacionService) {
        this.notificacionService = notificacionService;
    }

    @ApplicationModuleListener
    public void notificar(NotificacionPedida pedido) {
        notificacionService.notificar(pedido);
    }
}
