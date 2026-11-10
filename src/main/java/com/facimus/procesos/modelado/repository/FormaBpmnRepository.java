package com.facimus.procesos.modelado.repository;

import java.util.List;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.facimus.procesos.common.RepositorioTenant;
import com.facimus.procesos.modelado.model.FormaBpmn;

/** PR 52: las formas BPMN DI de un proceso. */
public interface FormaBpmnRepository extends RepositorioTenant<FormaBpmn> {

    List<FormaBpmn> findAllByProcesoIdAndEmpresaIdOrderByIdAsc(Long procesoId, Long empresaId);

    /** Se recalculan enteras: las de antes se borran de una vez. */
    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query("delete from FormaBpmn f where f.procesoId = :procesoId and f.empresa.id = :empresaId")
    int borrarDelProceso(@Param("procesoId") Long procesoId, @Param("empresaId") Long empresaId);
}
