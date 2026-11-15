package com.facimus.procesos.gestion.dto.response;

import java.time.LocalDateTime;

import com.facimus.procesos.common.notificaciones.TipoDeNotificacion;

import io.swagger.v3.oas.annotations.media.Schema;

/** PR 63: una notificacion, como la ve su destinatario. */
@Schema(description = "A notification of the signed-in person. It carries no text of a comment: the web app opens "
        + "what it points to")
public record NotificacionResponse(
        @Schema(example = "41") Long id,
        @Schema(description = "What happened: a comment (MENCION, RESPUESTA, HILO_RESUELTO), a review request "
                + "(APROBACION_PEDIDA, SOLICITUD_APROBADA, SOLICITUD_PUBLICADA, SOLICITUD_SIN_PUBLICAR, "
                + "CAMBIOS_PEDIDOS, SOLICITUD_RECHAZADA) or a review date (RECERTIFICACION_PENDIENTE, "
                + "RECERTIFICACION_VENCIDA)",
                example = "MENCION") TipoDeNotificacion tipo,
        @Schema(example = "7") Long procesoId,
        @Schema(description = "The name of the process", example = "Order fulfillment") String proceso,
        @Schema(description = "What it points to in the process, by type: the comment of a mention or a reply, the "
                + "thread that was resolved, or the review request; empty for a review date, which points to the "
                + "process itself", example = "12") Long referenciaId,
        @Schema(example = "2") Long autorId,
        @Schema(description = "Who caused it", example = "Ana") String autor,
        @Schema(example = "2026-11-12T10:00:00") LocalDateTime creadaEn,
        @Schema(description = "When it was read; empty while it is not", example = "2026-11-12T10:05:00")
        LocalDateTime leidaEn) {
}
