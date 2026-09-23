package com.facimus.procesos.modelado.service;

import java.util.List;

import com.facimus.procesos.modelado.dto.response.EventoResponse;
import com.facimus.procesos.modelado.model.TipoEvento;

/** HU-04 y HU-27: los eventos que abren, cierran y esperan en el flujo. */
public interface EventoService {

    EventoResponse crear(Long empresaId, Long usuarioId, Long laneId, String nombre, TipoEvento tipoEvento, int posX,
            int posY);

    EventoResponse editar(Long empresaId, Long usuarioId, Long eventoId, String nombre, TipoEvento tipoEvento,
            int posX, int posY, Long version);

    void eliminar(Long empresaId, Long usuarioId, Long eventoId);

    EventoResponse obtener(Long empresaId, Long eventoId);

    List<EventoResponse> listarPorLane(Long empresaId, Long laneId);
}
