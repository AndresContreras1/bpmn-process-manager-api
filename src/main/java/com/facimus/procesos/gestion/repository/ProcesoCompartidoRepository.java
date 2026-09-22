package com.facimus.procesos.gestion.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;

import com.facimus.procesos.common.RepositorioTenant;
import com.facimus.procesos.gestion.model.ProcesoCompartido;

/** Con quien comparte sus procesos una empresa. Todas las consultas son de la duena: filtran por su empresaId. */
public interface ProcesoCompartidoRepository extends RepositorioTenant<ProcesoCompartido> {

    /** Las empresas con acceso a un proceso, con los datos de cada una en la misma consulta. */
    @EntityGraph(attributePaths = "empresaInvitada")
    List<ProcesoCompartido> findAllByProcesoIdAndEmpresaIdOrderByIdAsc(Long procesoId, Long empresaId);

    @EntityGraph(attributePaths = "empresaInvitada")
    Optional<ProcesoCompartido> findByProcesoIdAndEmpresaInvitadaIdAndEmpresaId(Long procesoId, Long empresaInvitadaId,
            Long empresaId);

    boolean existsByProcesoIdAndEmpresaInvitadaIdAndEmpresaId(Long procesoId, Long empresaInvitadaId, Long empresaId);
}
