package com.facimus.procesos.modelado.repository;

import java.util.List;

import com.facimus.procesos.common.RepositorioTenant;
import com.facimus.procesos.modelado.model.Lane;

public interface LaneRepository extends RepositorioTenant<Lane> {

    List<Lane> findAllByPoolIdAndEmpresaIdOrderByOrdenAsc(Long poolId, Long empresaId);

    long countByRolProcesoIdAndEmpresaId(Long rolProcesoId, Long empresaId);

    boolean existsByRolProcesoIdAndEmpresaId(Long rolProcesoId, Long empresaId);

    List<Lane> findAllByRolProcesoIdAndEmpresaId(Long rolProcesoId, Long empresaId);
}
