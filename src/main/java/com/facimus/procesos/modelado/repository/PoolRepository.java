package com.facimus.procesos.modelado.repository;

import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.facimus.procesos.common.RepositorioTenant;
import com.facimus.procesos.modelado.model.Pool;

public interface PoolRepository extends RepositorioTenant<Pool> {

    List<Pool> findAllByProcesoIdAndEmpresaIdOrderByOrdenAsc(Long procesoId, Long empresaId);

    /** Posicion del proximo pool: despues del ultimo, aunque se haya eliminado alguno del medio. */
    @Query("""
            select coalesce(max(p.orden), -1) + 1 from Pool p
            where p.proceso.id = :procesoId and p.empresa.id = :empresaId
            """)
    int siguienteOrden(@Param("procesoId") Long procesoId, @Param("empresaId") Long empresaId);
}
