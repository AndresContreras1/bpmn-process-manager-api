package com.facimus.procesos.gestion.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.Query;

import com.facimus.procesos.common.RepositorioTenant;
import com.facimus.procesos.gestion.model.Sesion;

public interface SesionRepository extends RepositorioTenant<Sesion> {

    Optional<Sesion> findByCodigoAndUsuarioIdAndEmpresaId(String codigo, Long usuarioId, Long empresaId);

    List<Sesion> findAllByUsuarioIdAndEmpresaIdAndFechaCierreIsNull(Long usuarioId, Long empresaId);

    /**
     * Al arrancar la aplicacion: las sesiones cerradas hace poco, cuyos access tokens todavia no vencen. Cruza
     * empresas a proposito, porque solo devuelve codigos para rechazar esos tokens.
     */
    @Query("select s.codigo from Sesion s where s.fechaCierre > :desde")
    List<String> codigosCerradosDesde(LocalDateTime desde);
}
