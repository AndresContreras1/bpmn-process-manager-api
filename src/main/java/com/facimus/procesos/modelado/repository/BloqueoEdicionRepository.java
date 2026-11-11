package com.facimus.procesos.modelado.repository;

import java.util.Optional;

import com.facimus.procesos.common.RepositorioTenant;
import com.facimus.procesos.modelado.model.BloqueoEdicion;

/** PR 58: quien edita cada proceso. */
public interface BloqueoEdicionRepository extends RepositorioTenant<BloqueoEdicion> {

    Optional<BloqueoEdicion> findByProcesoIdAndEmpresaId(Long procesoId, Long empresaId);
}
