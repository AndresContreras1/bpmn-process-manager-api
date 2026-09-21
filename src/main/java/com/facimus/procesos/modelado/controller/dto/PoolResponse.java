package com.facimus.procesos.modelado.controller.dto;

import com.facimus.procesos.modelado.model.Pool;
import com.facimus.procesos.modelado.model.TipoParticipante;

public record PoolResponse(Long id, String nombre, TipoParticipante tipoParticipante, boolean cajaNegra,
        int orden, Long procesoId) {

    public static PoolResponse of(Pool p) {
        return new PoolResponse(p.getId(), p.getNombre(), p.getTipoParticipante(), p.isCajaNegra(),
                p.getOrden(), p.getProceso().getId());
    }
}
