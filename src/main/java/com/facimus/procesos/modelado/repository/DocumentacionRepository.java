package com.facimus.procesos.modelado.repository;

import java.util.List;
import java.util.Optional;

import com.facimus.procesos.common.RepositorioTenant;
import com.facimus.procesos.modelado.model.Documentacion;

/** PR 62: la documentacion de cada proceso y de sus elementos. */
public interface DocumentacionRepository extends RepositorioTenant<Documentacion> {

    List<Documentacion> findAllByProcesoIdAndEmpresaIdOrderByIdAsc(Long procesoId, Long empresaId);

    Optional<Documentacion> findByProcesoIdAndBpmnIdAndEmpresaId(Long procesoId, String bpmnId, Long empresaId);

    Optional<Documentacion> findByProcesoIdAndBpmnIdIsNullAndEmpresaId(Long procesoId, Long empresaId);
}
