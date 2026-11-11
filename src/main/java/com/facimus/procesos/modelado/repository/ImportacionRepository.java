package com.facimus.procesos.modelado.repository;

import java.util.List;

import com.facimus.procesos.common.RepositorioTenant;
import com.facimus.procesos.modelado.model.Importacion;

/** PR 54: los archivos importados en un proceso, el ultimo primero. */
public interface ImportacionRepository extends RepositorioTenant<Importacion> {

    List<Importacion> findAllByProcesoIdAndEmpresaIdOrderByFechaDesc(Long procesoId, Long empresaId);
}
