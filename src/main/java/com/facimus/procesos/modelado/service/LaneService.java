package com.facimus.procesos.modelado.service;

import java.util.List;

import com.facimus.procesos.modelado.dto.response.LaneResponse;

/** HU-22 y HU-24: lanes (divisiones internas de un pool). */
public interface LaneService {

    LaneResponse crear(Long empresaId, Long usuarioId, Long poolId, String nombre, Long rolProcesoId);

    LaneResponse editar(Long empresaId, Long usuarioId, Long laneId, String nombre, Long rolProcesoId, Long version);

    /** R-43: coloca las lanes del pool en el orden de la lista, que tiene que traerlas todas. */
    List<LaneResponse> reordenar(Long empresaId, Long usuarioId, Long poolId, List<Long> ids);

    void eliminar(Long empresaId, Long usuarioId, Long laneId);

    List<LaneResponse> listarPorPool(Long empresaId, Long poolId);

    LaneResponse obtener(Long empresaId, Long laneId);
}
