package com.facimus.procesos.modelado.repository;

import java.util.List;

import com.facimus.procesos.common.RepositorioTenant;
import com.facimus.procesos.modelado.model.Documento;

/** PR 61: los documentos de cada proceso. */
public interface DocumentoRepository extends RepositorioTenant<Documento> {

    List<Documento> findAllByProcesoIdAndEmpresaIdOrderByNombreAscIdAsc(Long procesoId, Long empresaId);

    List<Documento> findAllByProcesoIdAndBpmnIdAndEmpresaIdOrderByNombreAscIdAsc(Long procesoId, String bpmnId,
            Long empresaId);
}
