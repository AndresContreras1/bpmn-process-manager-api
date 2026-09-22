package com.facimus.procesos.modelado.service;

import java.util.List;

import com.facimus.procesos.modelado.dto.response.ActividadResponse;

/** HU-08 a HU-10: actividades (tareas del proceso). */
public interface ActividadService {

    ActividadResponse crear(Long empresaId, Long laneId, String nombre, String descripcion, int posX, int posY);

    ActividadResponse editar(Long empresaId, Long actividadId, String nombre, String descripcion, int posX,
            int posY, Long version);

    void eliminar(Long empresaId, Long actividadId);

    ActividadResponse obtener(Long empresaId, Long actividadId);

    List<ActividadResponse> listarPorLane(Long empresaId, Long laneId);
}
