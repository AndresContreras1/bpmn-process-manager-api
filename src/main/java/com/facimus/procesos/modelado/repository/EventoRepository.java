package com.facimus.procesos.modelado.repository;

import java.util.List;

import com.facimus.procesos.common.RepositorioTenant;
import com.facimus.procesos.modelado.model.Evento;

/** Consultas sobre el subtipo Evento: Hibernate filtra por el discriminador tipo_nodo. */
public interface EventoRepository extends RepositorioTenant<Evento> {

    List<Evento> findAllByLaneIdAndEmpresaId(Long laneId, Long empresaId);

    List<Evento> findAllByLane_Pool_ProcesoIdAndEmpresaIdOrderByIdAsc(Long procesoId, Long empresaId);
}
