package com.facimus.procesos.modelado.dto.response;

import com.facimus.procesos.modelado.model.Gateway;
import com.facimus.procesos.modelado.model.TipoGateway;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A gateway of the process")
public record GatewayResponse(
        @Schema(example = "12") Long id,
        @Schema(example = "Payment approved?") String nombre,
        @Schema(example = "EXCLUSIVO") TipoGateway tipoGateway,
        @Schema(example = "420") int posicionX,
        @Schema(example = "80") int posicionY,
        @Schema(example = "3") Long laneId) {

    public static GatewayResponse of(Gateway g) {
        return new GatewayResponse(g.getId(), g.getNombre(), g.getTipoGateway(), g.getPosicionX(),
                g.getPosicionY(), g.getLane().getId());
    }
}
