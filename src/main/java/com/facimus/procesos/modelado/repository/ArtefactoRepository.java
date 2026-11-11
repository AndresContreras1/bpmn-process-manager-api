package com.facimus.procesos.modelado.repository;

import java.util.List;

import com.facimus.procesos.common.RepositorioTenant;
import com.facimus.procesos.modelado.model.Artefacto;

/** PR 55: los artefactos de un proceso. */
public interface ArtefactoRepository extends RepositorioTenant<Artefacto> {

    List<Artefacto> findAllByProcesoIdAndEmpresaIdOrderByIdAsc(Long procesoId, Long empresaId);

    List<Artefacto> findAllByPool_IdAndEmpresaId(Long poolId, Long empresaId);

    boolean existsByLane_IdAndEmpresaId(Long laneId, Long empresaId);
}
