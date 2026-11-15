package com.facimus.procesos.gestion.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** PR 63: cuantas notificaciones esperan, para el numero de la campana. */
@Schema(description = "How many notifications of the signed-in person are still unread")
public record ConteoDeNotificacionesResponse(@Schema(example = "3") long noLeidas) {
}
