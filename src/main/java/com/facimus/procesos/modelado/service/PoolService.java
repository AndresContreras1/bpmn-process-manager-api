package com.facimus.procesos.modelado.service;

import java.util.List;

import com.facimus.procesos.modelado.dto.response.PoolResponse;
import com.facimus.procesos.modelado.model.TipoParticipante;

/** HU-21 y HU-23: pools (participantes del proceso). */
public interface PoolService {

    PoolResponse crear(Long empresaId, Long procesoId, String nombre, TipoParticipante tipoParticipante,
            boolean cajaNegra);

    PoolResponse editar(Long empresaId, Long poolId, String nombre, TipoParticipante tipoParticipante,
            Long version);

    void eliminar(Long empresaId, Long poolId);

    List<PoolResponse> listarPorProceso(Long empresaId, Long procesoId);

    PoolResponse obtener(Long empresaId, Long poolId);
}
