package com.facimus.procesos.gestion.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.facimus.procesos.common.RepositorioTenant;
import com.facimus.procesos.gestion.model.RolProceso;

public interface RolProcesoRepository extends RepositorioTenant<RolProceso> {

    Page<RolProceso> findAllByEmpresaIdAndActivoTrue(Long empresaId, Pageable pageable);

    Page<RolProceso> findAllByEmpresaIdAndActivoTrueAndNombreContainingIgnoreCase(Long empresaId, String nombre,
            Pageable pageable);

    Optional<RolProceso> findByIdAndEmpresaIdAndActivoTrue(Long id, Long empresaId);

    boolean existsByEmpresaIdAndNombreIgnoreCaseAndActivoTrue(Long empresaId, String nombre);

    /** Al renombrar: otro rol activo de la empresa ya usa ese nombre. */
    boolean existsByEmpresaIdAndNombreIgnoreCaseAndActivoTrueAndIdNot(Long empresaId, String nombre, Long id);
}
