package com.facimus.procesos.modelado.service;

import java.util.List;

import com.facimus.procesos.modelado.dto.response.EventoResponse;
import com.facimus.procesos.modelado.model.TipoEvento;

/** HU-04 y HU-27: los eventos que abren, cierran y esperan en el flujo. */
public interface EventoService {

    EventoResponse crear(Long empresaId, Long usuarioId, Long laneId, String nombre, TipoEvento tipoEvento, int posX,
            int posY);

    /** PR 55: con lo que dice un evento de temporizador; un evento de borde va pegado a una actividad. */
    EventoResponse crear(Long empresaId, Long usuarioId, Long laneId, String nombre, TipoEvento tipoEvento, int posX,
            int posY, DatosDeTemporizador temporizador);

    EventoResponse editar(Long empresaId, Long usuarioId, Long eventoId, String nombre, TipoEvento tipoEvento,
            Long laneId, int posX, int posY, Long version);

    EventoResponse editar(Long empresaId, Long usuarioId, Long eventoId, String nombre, TipoEvento tipoEvento,
            Long laneId, int posX, int posY, Long version, DatosDeTemporizador temporizador);

    void eliminar(Long empresaId, Long usuarioId, Long eventoId);

    EventoResponse obtener(Long empresaId, Long eventoId);

    List<EventoResponse> listarPorLane(Long empresaId, Long laneId);
}
