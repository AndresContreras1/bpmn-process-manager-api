package com.facimus.procesos.modelado.repository;

import java.util.List;

import com.facimus.procesos.common.RepositorioTenant;
import com.facimus.procesos.modelado.model.Gateway;

/** Consultas sobre el subtipo Gateway: Hibernate filtra por el discriminador tipo_nodo. */
public interface GatewayRepository extends RepositorioTenant<Gateway> {

    List<Gateway> findAllByLaneIdAndEmpresaId(Long laneId, Long empresaId);

    List<Gateway> findAllByLane_Pool_ProcesoIdAndEmpresaIdOrderByIdAsc(Long procesoId, Long empresaId);
}
