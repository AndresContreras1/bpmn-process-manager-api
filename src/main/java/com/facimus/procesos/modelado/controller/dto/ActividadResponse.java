package com.facimus.procesos.modelado.controller.dto;

import com.facimus.procesos.modelado.model.Actividad;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "An activity (task) of the process")
public record ActividadResponse(
        @Schema(example = "10") Long id,
        @Schema(example = "Pick and pack items") String nombre,
        @Schema(example = "Collect the items and prepare the package.") String descripcion,
        @Schema(example = "580") int posicionX,
        @Schema(example = "200") int posicionY,
        @Schema(example = "4") Long laneId) {

    public static ActividadResponse of(Actividad a) {
        return new ActividadResponse(a.getId(), a.getNombre(), a.getDescripcion(), a.getPosicionX(),
                a.getPosicionY(), a.getLane().getId());
    }
}
