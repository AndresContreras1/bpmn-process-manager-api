package com.facimus.procesos.modelado.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.facimus.procesos.common.RepositorioTenant;
import com.facimus.procesos.modelado.model.Lane;

public interface LaneRepository extends RepositorioTenant<Lane> {

    boolean existsByPoolIdAndEmpresaId(Long poolId, Long empresaId);

    /** Trae el rol de cada lane en la misma consulta: el listado muestra su nombre. */
    @EntityGraph(attributePaths = "rolProceso")
    List<Lane> findAllByPoolIdAndEmpresaIdOrderByOrdenAsc(Long poolId, Long empresaId);

    /** Las lanes de todo un proceso, en el orden del diagrama y con su rol, en una sola consulta. */
    @EntityGraph(attributePaths = "rolProceso")
    @Query("""
            select l from Lane l
            where l.pool.proceso.id = :procesoId and l.empresa.id = :empresaId
            order by l.pool.orden, l.orden
            """)
    List<Lane> delProcesoEnOrden(@Param("procesoId") Long procesoId, @Param("empresaId") Long empresaId);

    /** Posicion de la proxima lane: despues de la ultima, aunque se haya eliminado alguna del medio. */
    @Query("""
            select coalesce(max(l.orden), -1) + 1 from Lane l
            where l.pool.id = :poolId and l.empresa.id = :empresaId
            """)
    int siguienteOrden(@Param("poolId") Long poolId, @Param("empresaId") Long empresaId);

    /** Procesos activos con al menos una lane del rol: varias lanes de un proceso cuentan una vez. */
    @Query("""
            select count(distinct p.id) from Lane l join l.pool po join po.proceso p
            where l.rolProceso.id = :rolId and l.empresa.id = :empresaId and p.activo = true
            """)
    long contarProcesosActivosDelRol(@Param("empresaId") Long empresaId, @Param("rolId") Long rolId);

    @Query("""
            select distinct p.nombre from Lane l join l.pool po join po.proceso p
            where l.rolProceso.id = :rolId and l.empresa.id = :empresaId and p.activo = true
            order by p.nombre
            """)
    List<String> nombresDeProcesosActivosDelRol(@Param("empresaId") Long empresaId, @Param("rolId") Long rolId);

    /** El mismo conteo para varios roles a la vez; un rol sin procesos activos no aparece. */
    @Query("""
            select new com.facimus.procesos.modelado.repository.ProcesosDelRol(l.rolProceso.id, count(distinct p.id))
            from Lane l join l.pool po join po.proceso p
            where l.rolProceso.id in :rolIds and l.empresa.id = :empresaId and p.activo = true
            group by l.rolProceso.id
            """)
    List<ProcesosDelRol> contarProcesosActivosPorRol(@Param("empresaId") Long empresaId,
            @Param("rolIds") Collection<Long> rolIds);
}
