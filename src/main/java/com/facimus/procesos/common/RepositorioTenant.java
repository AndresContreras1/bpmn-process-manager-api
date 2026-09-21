package com.facimus.procesos.common;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

/**
 * Contrato base para repositorios de entidades que cuelgan de Empresa.
 * Obliga a que toda consulta puntual pase el empresaId, para no exponer
 * por accidente un findById sin filtro de tenant.
 */
@NoRepositoryBean
public interface RepositorioTenant<T extends EntidadEmpresa> extends JpaRepository<T, Long> {

    Optional<T> findByIdAndEmpresaId(Long id, Long empresaId);

    List<T> findAllByEmpresaId(Long empresaId);

    boolean existsByIdAndEmpresaId(Long id, Long empresaId);
}
