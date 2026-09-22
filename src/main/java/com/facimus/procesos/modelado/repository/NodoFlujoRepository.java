package com.facimus.procesos.modelado.repository;

import com.facimus.procesos.common.RepositorioTenant;
import com.facimus.procesos.modelado.model.NodoFlujo;

/** Actividades y gateways juntos: para los arcos, que unen nodos de cualquier subtipo, y las reglas comunes. */
public interface NodoFlujoRepository extends RepositorioTenant<NodoFlujo> {

    boolean existsByNombreIgnoreCaseAndLane_Pool_ProcesoIdAndEmpresaId(String nombre, Long procesoId, Long empresaId);

    /** Al renombrar un nodo: el nombre choca con otro nodo del proceso, no con el suyo. */
    boolean existsByNombreIgnoreCaseAndLane_Pool_ProcesoIdAndEmpresaIdAndIdNot(String nombre, Long procesoId,
            Long empresaId, Long id);

    boolean existsByLaneIdAndEmpresaId(Long laneId, Long empresaId);

    boolean existsByLane_Pool_IdAndEmpresaId(Long poolId, Long empresaId);
}
