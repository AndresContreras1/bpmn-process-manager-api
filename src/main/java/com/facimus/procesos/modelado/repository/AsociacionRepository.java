package com.facimus.procesos.modelado.repository;

import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.facimus.procesos.common.RepositorioTenant;
import com.facimus.procesos.modelado.model.Asociacion;

/** PR 55: las asociaciones de un proceso. */
public interface AsociacionRepository extends RepositorioTenant<Asociacion> {

    List<Asociacion> findAllByProcesoIdAndEmpresaIdOrderByIdAsc(Long procesoId, Long empresaId);

    /** Las que salen del elemento o llegan a el: se van con el. */
    @Query("select a from Asociacion a where a.empresa.id = :empresaId and a.proceso.id = :procesoId "
            + "and (a.origen = :bpmnId or a.destino = :bpmnId) order by a.id")
    List<Asociacion> queTocan(@Param("empresaId") Long empresaId, @Param("procesoId") Long procesoId,
            @Param("bpmnId") String bpmnId);
}
