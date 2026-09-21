package com.facimus.procesos.gestion.repository;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;

import com.facimus.procesos.common.RepositorioTenant;
import com.facimus.procesos.gestion.model.HistorialCambio;

public interface HistorialCambioRepository extends RepositorioTenant<HistorialCambio> {

    /** Trae el autor de cada cambio en la misma consulta: el historial muestra su nombre. */
    @EntityGraph(attributePaths = "autor")
    List<HistorialCambio> findAllByProcesoIdAndEmpresaIdOrderByFechaCambioDesc(Long procesoId, Long empresaId);
}
