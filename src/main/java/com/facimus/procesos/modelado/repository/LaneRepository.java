package com.facimus.procesos.modelado.repository;

import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.facimus.procesos.common.RepositorioTenant;
import com.facimus.procesos.modelado.model.Lane;

public interface LaneRepository extends RepositorioTenant<Lane> {

    List<Lane> findAllByPoolIdAndEmpresaIdOrderByOrdenAsc(Long poolId, Long empresaId);

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
