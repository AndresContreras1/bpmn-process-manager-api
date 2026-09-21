package com.facimus.procesos.gestion.repository;

import java.util.List;

import com.facimus.procesos.common.RepositorioTenant;
import com.facimus.procesos.gestion.model.HistorialCambio;

public interface HistorialCambioRepository extends RepositorioTenant<HistorialCambio> {

    List<HistorialCambio> findAllByProcesoIdAndEmpresaIdOrderByFechaCambioDesc(Long procesoId, Long empresaId);
}
