package com.facimus.procesos.modelado.repository;

import java.util.List;

import com.facimus.procesos.common.RepositorioTenant;
import com.facimus.procesos.modelado.model.Actividad;

/** Consultas sobre el subtipo Actividad: Hibernate filtra por el discriminador tipo_nodo. */
public interface ActividadRepository extends RepositorioTenant<Actividad> {

    List<Actividad> findAllByLaneIdAndEmpresaId(Long laneId, Long empresaId);

    List<Actividad> findAllByLane_Pool_ProcesoIdAndEmpresaIdOrderByIdAsc(Long procesoId, Long empresaId);
}
