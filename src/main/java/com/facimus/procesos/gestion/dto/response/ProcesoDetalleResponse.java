package com.facimus.procesos.gestion.dto.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Un proceso con su historial. F22: y el nombre de su dueno, que el portal de lectura muestra en la ficha: quien solo
 * lee no puede listar a las personas de la tienda, asi que el id no le diria a quien preguntar.
 */
@Schema(description = "A process together with its change history")
public record ProcesoDetalleResponse(
        ProcesoResponse proceso,
        @Schema(description = "Changes, newest first") List<HistorialCambioResponse> historial,
        @Schema(description = "Name of the person who owns it; empty when nobody does", example = "Ana Gomez")
        String duenoNombre) {
}
