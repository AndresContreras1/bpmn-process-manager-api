package com.facimus.procesos.gestion.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.facimus.procesos.gestion.model.Empresa;

/** Unico repositorio sin filtro de tenant: Empresa es la raiz. */
public interface EmpresaRepository extends JpaRepository<Empresa, Long> {

    Optional<Empresa> findByNit(String nit);

    boolean existsByNit(String nit);
}
