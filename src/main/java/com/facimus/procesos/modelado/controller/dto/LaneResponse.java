package com.facimus.procesos.modelado.controller.dto;

import com.facimus.procesos.modelado.model.Lane;

public record LaneResponse(Long id, String nombre, int orden, Long poolId, Long rolProcesoId,
        String rolProcesoNombre) {

    public static LaneResponse of(Lane l) {
        return new LaneResponse(l.getId(), l.getNombre(), l.getOrden(), l.getPool().getId(),
                l.getRolProceso().getId(), l.getRolProceso().getNombre());
    }
}
