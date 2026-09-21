package com.facimus.procesos.modelado.repository;

import com.facimus.procesos.common.RepositorioTenant;
import com.facimus.procesos.modelado.model.NodoFlujo;

/** Actividades y gateways juntos: para los arcos, que unen nodos de cualquier subtipo, y las reglas comunes. */
public interface NodoFlujoRepository extends RepositorioTenant<NodoFlujo> {

    boolean existsByNombreIgnoreCaseAndLane_Pool_ProcesoIdAndEmpresaId(String nombre, Long procesoId, Long empresaId);

    boolean existsByLaneIdAndEmpresaId(Long laneId, Long empresaId);

    boolean existsByLane_Pool_IdAndEmpresaId(Long poolId, Long empresaId);
}
