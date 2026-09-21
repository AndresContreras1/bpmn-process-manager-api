package com.facimus.procesos.modelado.controller.dto;

import com.facimus.procesos.modelado.model.Arco;

public record ArcoResponse(Long id, String etiqueta, String condicion, Long origenId, Long destinoId,
        Long poolId) {

    public static ArcoResponse of(Arco a) {
        return new ArcoResponse(a.getId(), a.getEtiqueta(), a.getCondicion(), a.getOrigen().getId(),
                a.getDestino().getId(), a.getPool().getId());
    }
}
