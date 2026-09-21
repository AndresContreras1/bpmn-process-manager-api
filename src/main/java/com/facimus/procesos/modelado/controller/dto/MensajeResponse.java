package com.facimus.procesos.modelado.controller.dto;

import com.facimus.procesos.modelado.model.Mensaje;

public record MensajeResponse(Long id, String nombre, String contenido, Long poolOrigenId,
        Long poolDestinoId, Long procesoId) {

    public static MensajeResponse of(Mensaje m) {
        return new MensajeResponse(m.getId(), m.getNombre(), m.getContenido(), m.getPoolOrigen().getId(),
                m.getPoolDestino().getId(), m.getProceso().getId());
    }
}
