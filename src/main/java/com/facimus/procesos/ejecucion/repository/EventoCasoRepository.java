package com.facimus.procesos.ejecucion.repository;

import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.facimus.procesos.common.RepositorioTenant;
import com.facimus.procesos.ejecucion.model.EventoCaso;
import com.facimus.procesos.ejecucion.model.TipoEventoCaso;

/** La bitacora de los casos. Solo se inserta y se lee en orden: lo que paso no se corrige. */
public interface EventoCasoRepository extends RepositorioTenant<EventoCaso> {

    List<EventoCaso> findAllByCasoIdAndEmpresaIdOrderByIdAsc(Long casoId, Long empresaId);

    /**
     * Cuantas veces paso cada una de esas cosas, de un proceso o de toda la tienda. El tablero pregunta por las
     * tres que dejan un pedido parado o torcido, y las cuenta en una sola consulta.
     */
    @Query("""
            select e.tipo, count(e) from EventoCaso e
            where e.empresa.id = :empresaId
              and e.tipo in :tipos
              and (:procesoId is null or e.caso.proceso.id = :procesoId)
            group by e.tipo
            """)
    List<Object[]> contarPorTipo(@Param("empresaId") Long empresaId, @Param("procesoId") Long procesoId,
            @Param("tipos") List<TipoEventoCaso> tipos);
}
