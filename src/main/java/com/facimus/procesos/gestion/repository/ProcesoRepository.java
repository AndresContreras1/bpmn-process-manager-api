package com.facimus.procesos.gestion.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.facimus.procesos.common.RepositorioTenant;
import com.facimus.procesos.gestion.model.Proceso;

public interface ProcesoRepository extends RepositorioTenant<Proceso>, JpaSpecificationExecutor<Proceso> {

    boolean existsByEmpresaIdAndNombreIgnoreCaseAndActivoTrue(Long empresaId, String nombre);

    Optional<Proceso> findByIdAndEmpresaIdAndActivoTrue(Long id, Long empresaId);
}
