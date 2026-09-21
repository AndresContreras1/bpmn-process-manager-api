package com.facimus.procesos.gestion.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.facimus.procesos.gestion.model.Empresa;

public interface EmpresaRepository extends JpaRepository<Empresa, Long> {

    boolean existsByNit(String nit);
}
