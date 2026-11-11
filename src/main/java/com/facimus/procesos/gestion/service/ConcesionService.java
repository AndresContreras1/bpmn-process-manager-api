package com.facimus.procesos.gestion.service;

import java.util.List;

import com.facimus.procesos.common.model.Accion;
import com.facimus.procesos.gestion.dto.response.ConcesionResponse;
import com.facimus.procesos.gestion.model.SujetoConcesion;

/** PR 60: quien puede hacer que sobre una carpeta o un proceso. */
public interface ConcesionService {

    List<ConcesionResponse> listar(Long empresaId, Recurso recurso);

    ConcesionResponse conceder(Actor actor, Recurso recurso, Accion accion, SujetoConcesion sujeto, Long usuarioId,
            Long rolProcesoId);

    void retirar(Actor actor, Long concesionId);
}
