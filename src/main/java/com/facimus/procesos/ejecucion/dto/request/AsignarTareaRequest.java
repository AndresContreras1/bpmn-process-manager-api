package com.facimus.procesos.ejecucion.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Quien se reserva una tarea. Es una nota para el equipo, no un permiso: la bandeja la sigue viendo el rol entero y
 * la completa cualquier administrador o editor de la tienda (D13).
 */
@Schema(description = "The user who takes the task; empty releases it")
public record AsignarTareaRequest(
        @Schema(description = "A user of the store; empty leaves the task free again", example = "5")
        Long usuarioId) {
}
