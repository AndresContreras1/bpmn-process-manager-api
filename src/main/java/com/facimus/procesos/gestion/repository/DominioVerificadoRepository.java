package com.facimus.procesos.gestion.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;

import com.facimus.procesos.common.RepositorioTenant;
import com.facimus.procesos.gestion.model.DominioVerificado;

public interface DominioVerificadoRepository extends RepositorioTenant<DominioVerificado> {

    /** De que tienda es un dominio ya verificado; cruza tiendas a proposito, porque es lo que se pregunta. */
    @EntityGraph(attributePaths = "empresa")
    Optional<DominioVerificado> findByDominioAndVerificadoEnIsNotNull(String dominio);

    List<DominioVerificado> findAllByEmpresaIdOrderByDominioAsc(Long empresaId);

    boolean existsByEmpresaIdAndDominio(Long empresaId, String dominio);
}
