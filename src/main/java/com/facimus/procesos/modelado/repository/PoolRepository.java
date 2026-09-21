package com.facimus.procesos.modelado.repository;

import java.util.List;

import com.facimus.procesos.common.RepositorioTenant;
import com.facimus.procesos.modelado.model.Pool;

public interface PoolRepository extends RepositorioTenant<Pool> {

    List<Pool> findAllByProcesoIdAndEmpresaIdOrderByOrdenAsc(Long procesoId, Long empresaId);
}
