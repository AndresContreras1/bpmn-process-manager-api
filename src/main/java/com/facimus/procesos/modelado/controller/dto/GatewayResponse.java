package com.facimus.procesos.modelado.controller.dto;

import com.facimus.procesos.modelado.model.Gateway;
import com.facimus.procesos.modelado.model.TipoGateway;

public record GatewayResponse(Long id, String nombre, TipoGateway tipoGateway, int posicionX, int posicionY,
        Long laneId) {

    public static GatewayResponse of(Gateway g) {
        return new GatewayResponse(g.getId(), g.getNombre(), g.getTipoGateway(), g.getPosicionX(),
                g.getPosicionY(), g.getLane().getId());
    }
}
