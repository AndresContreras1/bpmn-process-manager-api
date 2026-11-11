package com.facimus.procesos.modelado.repository;

import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.facimus.procesos.common.RepositorioTenant;
import com.facimus.procesos.modelado.model.Actividad;

/** Consultas sobre el subtipo Actividad: Hibernate filtra por el discriminador tipo_nodo. */
public interface ActividadRepository extends RepositorioTenant<Actividad> {

    List<Actividad> findAllByLaneIdAndEmpresaId(Long laneId, Long empresaId);

    List<Actividad> findAllByLane_Pool_ProcesoIdAndEmpresaIdOrderByIdAsc(Long procesoId, Long empresaId);

    /** PR 56: de que proceso a que proceso llama cada actividad de llamada de la tienda. */
    @Query("select new com.facimus.procesos.modelado.repository.ActividadRepository$Llamada("
            + "a.lane.pool.proceso.id, a.procesoLlamado.id, a.procesoLlamado.nombre, a.procesoLlamado.activo, a.nombre)"
            + " from Actividad a where a.empresa.id = :empresaId and a.procesoLlamado is not null")
    List<Llamada> llamadasDeLaTienda(@Param("empresaId") Long empresaId);

    /** Una actividad de llamada: el proceso donde esta y el que corre. */
    record Llamada(Long procesoId, Long llamadoId, String llamadoNombre, boolean llamadoActivo, String actividad) {
    }
}
