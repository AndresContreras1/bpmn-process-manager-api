package com.facimus.procesos.gestion.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.facimus.procesos.common.RepositorioTenant;
import com.facimus.procesos.gestion.model.Proceso;

public interface ProcesoRepository extends RepositorioTenant<Proceso>, JpaSpecificationExecutor<Proceso> {

    boolean existsByEmpresaIdAndNombreIgnoreCaseAndActivoTrue(Long empresaId, String nombre);

    Optional<Proceso> findByIdAndEmpresaIdAndActivoTrue(Long id, Long empresaId);

    boolean existsByIdAndEmpresaIdAndActivoTrue(Long id, Long empresaId);

    /**
     * La puerta de lectura (HU-23): el proceso activo si es de la empresa o si su duena se lo compartio. Ningun cambio
     * pasa por aqui: para escribir se usa findByIdAndEmpresaIdAndActivoTrue, que solo encuentra los propios.
     */
    @Query("""
            select p from Proceso p
            where p.id = :id and p.activo = true
              and (p.empresa.id = :empresaId
                   or exists (select 1 from ProcesoCompartido c
                              where c.proceso = p and c.empresaInvitada.id = :empresaId))
            """)
    Optional<Proceso> paraLectura(@Param("id") Long id, @Param("empresaId") Long empresaId);

    /** Los procesos activos que otras empresas le comparten a esta, con su duena en la misma consulta. */
    @EntityGraph(attributePaths = "empresa")
    @Query(value = """
            select p from Proceso p
            where p.activo = true
              and exists (select 1 from ProcesoCompartido c where c.proceso = p and c.empresaInvitada.id = :empresaId)
            """, countQuery = """
            select count(p) from Proceso p
            where p.activo = true
              and exists (select 1 from ProcesoCompartido c where c.proceso = p and c.empresaInvitada.id = :empresaId)
            """)
    Page<Proceso> compartidosCon(@Param("empresaId") Long empresaId, Pageable pageable);
}
