package com.facimus.procesos.modelado.service;

import java.util.List;

import com.facimus.procesos.modelado.dto.response.GatewayResponse;
import com.facimus.procesos.modelado.model.TipoGateway;

/** HU-14 a HU-16: gateways (puntos de decision). */
public interface GatewayService {

    GatewayResponse crear(Long empresaId, Long laneId, String nombre, TipoGateway tipoGateway, int posX, int posY);

    GatewayResponse editar(Long empresaId, Long gatewayId, String nombre, TipoGateway tipoGateway, int posX,
            int posY, Long version);

    void eliminar(Long empresaId, Long gatewayId);

    GatewayResponse obtener(Long empresaId, Long gatewayId);

    List<GatewayResponse> listarPorLane(Long empresaId, Long laneId);
}
