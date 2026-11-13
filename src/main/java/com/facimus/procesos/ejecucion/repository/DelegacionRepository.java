package com.facimus.procesos.ejecucion.repository;

import java.util.List;
import java.util.Optional;

import com.facimus.procesos.common.RepositorioTenant;
import com.facimus.procesos.ejecucion.model.Delegacion;
import com.facimus.procesos.ejecucion.model.EstadoDelegacion;

/** PR 70: las delegaciones de las tareas: la abierta de cada una y su historia. */
public interface DelegacionRepository extends RepositorioTenant<Delegacion> {

    /** La delegacion abierta de una tarea; hay a lo sumo una (uq_delegaciones_activa). */
    Optional<Delegacion> findFirstByTareaIdAndEmpresaIdAndEstado(Long tareaId, Long empresaId,
            EstadoDelegacion estado);

    /** Las delegaciones de una tarea, de la primera a la ultima. */
    List<Delegacion> findAllByTareaIdAndEmpresaIdOrderByIdAsc(Long tareaId, Long empresaId);
}
