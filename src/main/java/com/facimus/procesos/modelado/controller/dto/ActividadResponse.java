package com.facimus.procesos.modelado.controller.dto;

import com.facimus.procesos.modelado.model.Actividad;

public record ActividadResponse(Long id, String nombre, String descripcion, int posicionX, int posicionY,
        Long laneId) {

    public static ActividadResponse of(Actividad a) {
        return new ActividadResponse(a.getId(), a.getNombre(), a.getDescripcion(), a.getPosicionX(),
                a.getPosicionY(), a.getLane().getId());
    }
}
