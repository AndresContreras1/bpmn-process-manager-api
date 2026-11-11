package com.facimus.procesos.gestion.repository;

import java.util.List;

import com.facimus.procesos.common.RepositorioTenant;
import com.facimus.procesos.gestion.model.Carpeta;

public interface CarpetaRepository extends RepositorioTenant<Carpeta> {

    List<Carpeta> findAllByEmpresaIdOrderByNombreAsc(Long empresaId);

    boolean existsByEmpresaIdAndPadreId(Long empresaId, Long padreId);
}
