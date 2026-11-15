package com.facimus.procesos.modelado.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** PR 63: una persona de la tienda, como se muestra junto a un comentario. */
@Schema(description = "A person of the store, as shown next to a comment")
public record PersonaResponse(@Schema(example = "2") Long id, @Schema(example = "Ana") String nombre) {
}
