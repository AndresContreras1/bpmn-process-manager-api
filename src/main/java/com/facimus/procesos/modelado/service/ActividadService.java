package com.facimus.procesos.modelado.service;

import java.util.List;

import com.facimus.procesos.modelado.dto.response.ActividadResponse;
import com.facimus.procesos.modelado.model.TipoActividad;

/** HU-08 a HU-10: actividades (tareas del proceso). */
public interface ActividadService {

    ActividadResponse crear(Long empresaId, Long usuarioId, Long laneId, String nombre, String descripcion,
            TipoActividad tipoActividad, int posX, int posY);

    ActividadResponse editar(Long empresaId, Long usuarioId, Long actividadId, String nombre, String descripcion,
            TipoActividad tipoActividad, int posX, int posY, Long version);

    void eliminar(Long empresaId, Long usuarioId, Long actividadId);

    ActividadResponse obtener(Long empresaId, Long actividadId);

    List<ActividadResponse> listarPorLane(Long empresaId, Long laneId);
}
