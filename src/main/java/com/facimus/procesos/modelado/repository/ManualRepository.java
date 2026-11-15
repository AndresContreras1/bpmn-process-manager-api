package com.facimus.procesos.modelado.repository;

import java.util.List;

import com.facimus.procesos.common.RepositorioTenant;
import com.facimus.procesos.modelado.model.Manual;

/** PR 62: los manuales pedidos de cada proceso. */
public interface ManualRepository extends RepositorioTenant<Manual> {

    List<Manual> findAllByProcesoIdAndEmpresaIdOrderByIdDesc(Long procesoId, Long empresaId);
}
