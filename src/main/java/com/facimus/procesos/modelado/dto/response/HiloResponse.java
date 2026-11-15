package com.facimus.procesos.modelado.dto.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/** PR 63: un hilo de comentarios: el primero y sus respuestas, de la mas vieja a la mas nueva. */
@Schema(description = "A comment thread: its first comment and its replies, oldest first")
public record HiloResponse(ComentarioResponse comentario, List<ComentarioResponse> respuestas) {
}
