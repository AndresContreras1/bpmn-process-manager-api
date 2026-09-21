package com.facimus.procesos.modelado.controller.dto;

import com.facimus.procesos.modelado.model.Correlacion;

public record CorrelacionResponse(Long id, String criterio, Long mensajeId) {

    public static CorrelacionResponse of(Correlacion c) {
        return new CorrelacionResponse(c.getId(), c.getCriterio(), c.getMensaje().getId());
    }
}
