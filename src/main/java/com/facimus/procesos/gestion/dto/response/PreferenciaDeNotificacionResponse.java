package com.facimus.procesos.gestion.dto.response;

import com.facimus.procesos.common.notificaciones.EnvioPorCorreo;
import com.facimus.procesos.common.notificaciones.TipoDeNotificacion;

import io.swagger.v3.oas.annotations.media.Schema;

/** PR 63: como llega por correo un tipo de notificacion. */
@Schema(description = "How one type of notification reaches the signed-in person by e-mail. In the app it always "
        + "arrives")
public record PreferenciaDeNotificacionResponse(
        @Schema(example = "MENCION") TipoDeNotificacion tipo,
        @Schema(description = "INMEDIATO, RESUMEN_DIARIO or NUNCA", example = "INMEDIATO") EnvioPorCorreo envio,
        @Schema(description = "Whether it is the default of the type because the person never chose one",
                example = "true") boolean porDefecto) {
}
