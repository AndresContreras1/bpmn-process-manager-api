package com.facimus.procesos.gestion.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.facimus.procesos.common.RepositorioTenant;
import com.facimus.procesos.gestion.model.Proceso;

import jakarta.persistence.LockModeType;

public interface ProcesoRepository extends RepositorioTenant<Proceso>, JpaSpecificationExecutor<Proceso> {

    boolean existsByEmpresaIdAndNombreIgnoreCaseAndActivoTrue(Long empresaId, String nombre);

    Optional<Proceso> findByIdAndEmpresaIdAndActivoTrue(Long id, Long empresaId);

    boolean existsByIdAndEmpresaIdAndActivoTrue(Long id, Long empresaId);

    /** PR 40: dos guardados del mismo borrador se hacen de a uno: si no, los dos pasarian el If-Match. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Proceso p where p.id = :id and p.empresa.id = :empresaId and p.activo = true")
    Optional<Proceso> bloquear(@Param("id") Long id, @Param("empresaId") Long empresaId);

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

    /** PR 59: cuantos procesos activos tiene cada carpeta, de una vez. */
    @Query("""
            select new com.facimus.procesos.gestion.repository.ProcesosPorCarpeta(p.carpetaId, count(p)) from Proceso p
            where p.empresa.id = :empresaId and p.activo = true and p.carpetaId is not null
            group by p.carpetaId
            """)
    List<ProcesosPorCarpeta> contarPorCarpeta(@Param("empresaId") Long empresaId);

    long countByCarpetaIdAndEmpresaIdAndActivoTrue(Long carpetaId, Long empresaId);

    /** PR 60: los procesos con una concesion propia, para resolver lo visible de una vez. */
    List<Proceso> findAllByIdInAndEmpresaId(java.util.Collection<Long> ids, Long empresaId);

    /** PR 59: las etiquetas en uso, para sugerirlas. */
    @Query(value = """
            select distinct etiqueta from procesos, unnest(etiquetas) as etiqueta
            where empresa_id = :empresaId and activo = true order by etiqueta
            """, nativeQuery = true)
    List<String> etiquetasEnUso(@Param("empresaId") Long empresaId);
}
