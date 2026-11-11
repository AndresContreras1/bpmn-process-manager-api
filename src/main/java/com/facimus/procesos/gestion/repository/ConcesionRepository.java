package com.facimus.procesos.gestion.repository;

import java.util.List;

import com.facimus.procesos.common.RepositorioTenant;
import com.facimus.procesos.gestion.model.Concesion;

public interface ConcesionRepository extends RepositorioTenant<Concesion> {

    boolean existsByEmpresaId(Long empresaId);

    List<Concesion> findAllByEmpresaIdAndProcesoIdOrderByIdAsc(Long empresaId, Long procesoId);

    List<Concesion> findAllByEmpresaIdAndCarpetaIdOrderByIdAsc(Long empresaId, Long carpetaId);
}
