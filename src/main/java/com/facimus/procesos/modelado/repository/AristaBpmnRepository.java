package com.facimus.procesos.modelado.repository;

import java.util.List;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.facimus.procesos.common.RepositorioTenant;
import com.facimus.procesos.modelado.model.AristaBpmn;

/** PR 52: las aristas BPMN DI de un proceso. */
public interface AristaBpmnRepository extends RepositorioTenant<AristaBpmn> {

    List<AristaBpmn> findAllByProcesoIdAndEmpresaIdOrderByIdAsc(Long procesoId, Long empresaId);

    /** Se recalculan enteras: las de antes se borran de una vez. */
    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query("delete from AristaBpmn a where a.procesoId = :procesoId and a.empresa.id = :empresaId")
    int borrarDelProceso(@Param("procesoId") Long procesoId, @Param("empresaId") Long empresaId);
}
