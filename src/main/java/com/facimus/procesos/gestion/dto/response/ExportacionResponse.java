package com.facimus.procesos.gestion.dto.response;

import java.time.LocalDateTime;

import com.facimus.procesos.gestion.model.EstadoExportacion;

import io.swagger.v3.oas.annotations.media.Schema;

/** PR 66: una exportacion completa de la tienda y en que va. El enlace para bajarla solo va por correo. */
@Schema(description = "A full export of the store: whether it is ready, its size, its SHA-256 and until when its "
        + "link works. The link itself only goes by e-mail, to whoever asked for it.")
public record ExportacionResponse(
        @Schema(example = "4") Long id,
        @Schema(description = "PENDIENTE while the queue builds it, LISTA while its link works, FALLIDA with a reason, "
                + "or VENCIDA once its link expired and the file was deleted", example = "LISTA")
        EstadoExportacion estado,
        @Schema(description = "Id of the administrator who asked for it", example = "2") Long pedidaPor,
        @Schema(example = "2026-11-12T10:00:00") LocalDateTime pedidaEn,
        @Schema(example = "2026-11-12T10:00:12") LocalDateTime listaEn,
        @Schema(description = "When the link stops working and the file is deleted, in UTC",
                example = "2026-11-13T10:00:12") LocalDateTime venceEn,
        @Schema(description = "Size of the ZIP in bytes", example = "184320") Long tamano,
        @Schema(description = "Files inside the ZIP, the manifest aside", example = "42") Integer archivos,
        @Schema(description = "SHA-256 of the whole ZIP, in hex", example = "9f86d081884c7d65...") String sha256,
        @Schema(example = "No se pudo leer el documento 12 del almacén.") String motivo) {
}
