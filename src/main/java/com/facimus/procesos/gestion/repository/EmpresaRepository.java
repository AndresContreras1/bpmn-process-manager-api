package com.facimus.procesos.gestion.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.facimus.procesos.gestion.model.Empresa;

public interface EmpresaRepository extends JpaRepository<Empresa, Long> {

    boolean existsByNit(String nit);

    /** HU-23: la empresa con la que se comparte un proceso se busca por su NIT. */
    Optional<Empresa> findByNit(String nit);
}
