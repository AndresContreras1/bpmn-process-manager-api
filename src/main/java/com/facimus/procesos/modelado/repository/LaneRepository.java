package com.facimus.procesos.modelado.repository;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.facimus.procesos.common.RepositorioTenant;
import com.facimus.procesos.modelado.model.Lane;

public interface LaneRepository extends RepositorioTenant<Lane> {

    /** Trae el rol de cada lane en la misma consulta: el listado muestra su nombre. */
    @EntityGraph(attributePaths = "rolProceso")
    List<Lane> findAllByPoolIdAndEmpresaIdOrderByOrdenAsc(Long poolId, Long empresaId);

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
}
