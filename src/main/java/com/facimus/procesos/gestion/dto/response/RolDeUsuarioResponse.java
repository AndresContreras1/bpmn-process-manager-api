package com.facimus.procesos.gestion.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Un rol de proceso de una persona. Solo su identidad: cuanto se usa ese rol y si se puede borrar son preguntas de
 * la pantalla de roles, no de la de una persona.
 */
@Schema(description = "A process role a user belongs to")
public record RolDeUsuarioResponse(
        @Schema(example = "2") Long id,
        @Schema(example = "Warehouse") String nombre,
        @Schema(example = "Picks, packs and ships the orders.") String descripcion) {
}
