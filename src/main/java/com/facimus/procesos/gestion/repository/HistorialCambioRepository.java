package com.facimus.procesos.gestion.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;

import com.facimus.procesos.common.RepositorioTenant;
import com.facimus.procesos.gestion.model.HistorialCambio;

public interface HistorialCambioRepository extends RepositorioTenant<HistorialCambio> {

    /** Trae el autor de cada cambio en la misma consulta: el historial muestra su nombre. */
    @EntityGraph(attributePaths = "autor")
    List<HistorialCambio> findAllByProcesoIdAndEmpresaIdOrderByFechaCambioDescIdDesc(Long procesoId, Long empresaId);

    /** Todo lo que paso en la tienda, de lo mas reciente a lo mas viejo. Crece sin parar, asi que va paginado. */
    @EntityGraph(attributePaths = "autor")
    Page<HistorialCambio> findAllByEmpresaIdOrderByFechaCambioDescIdDesc(Long empresaId, Pageable pageable);
}
