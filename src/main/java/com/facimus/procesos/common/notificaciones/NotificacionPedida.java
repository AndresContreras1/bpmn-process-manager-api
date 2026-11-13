package com.facimus.procesos.common.notificaciones;

import java.util.List;

/**
 * PR 63: algo que paso y que unas personas tienen que saber. Lo publica el modulo donde paso, en su transaccion, y
 * espera en el outbox hasta que gestion lo convierte en una notificacion por persona: si lo que paso se deshace, nadie
 * se entera, y lo que falle al notificar no deshace lo que paso. Lleva ids y no nombres ni textos: el outbox no guarda
 * nada que haya que borrar despues.
 *
 * @param autorId quien lo hizo; nunca se le notifica a si mismo
 * @param referenciaId lo que la notificacion senala dentro del proceso, como un comentario
 * @param destinatarios a quienes avisar; quien ya no puede ver el proceso no deberia estar
 */
public record NotificacionPedida(Long empresaId, TipoDeNotificacion tipo, Long autorId, Long procesoId,
        Long referenciaId, List<Long> destinatarios) {

    public NotificacionPedida {
        destinatarios = List.copyOf(destinatarios);
    }
}
