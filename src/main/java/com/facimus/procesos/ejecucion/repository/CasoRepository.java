package com.facimus.procesos.ejecucion.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.facimus.procesos.common.RepositorioTenant;
import com.facimus.procesos.ejecucion.dto.response.CasosPorEstadoResponse;
import com.facimus.procesos.ejecucion.model.Caso;

import jakarta.persistence.LockModeType;

/**
 * Los casos de una tienda. Las dos primeras consultas no tienen cuerpo aqui: son las consultas con nombre que
 * declara la entidad (D22), y Spring Data las encuentra por {@code Caso.<nombre del metodo>}. Si alguien renombra
 * una, el contexto no arranca.
 */
public interface CasoRepository extends RepositorioTenant<Caso>, JpaSpecificationExecutor<Caso> {

    /**
     * El listado de casos con su proceso y su version ya traidos: cada fila del listado los nombra, y sin esto
     * serian dos consultas mas por caso.
     */
    @Override
    @EntityGraph(attributePaths = {"proceso", "versionProceso"})
    Page<Caso> findAll(Specification<Caso> filtros, Pageable pagina);

    /** Los casos de un proceso que todavia tienen tokens vivos. */
    List<Caso> abiertosPorProceso(@Param("empresaId") Long empresaId, @Param("procesoId") Long procesoId);

    /**
     * Los casos por estado, de un proceso o de toda la tienda. Es una consulta agrupada y no una por estado: el
     * tablero se pinta entero de una vez o no vale la pena.
     */
    @Query("""
            select new com.facimus.procesos.ejecucion.dto.response.CasosPorEstadoResponse(c.estado, count(c))
            from Caso c
            where c.empresa.id = :empresaId
              and (:procesoId is null or c.proceso.id = :procesoId)
            group by c.estado
            order by c.estado
            """)
    List<CasosPorEstadoResponse> casosPorEstado(@Param("empresaId") Long empresaId,
            @Param("procesoId") Long procesoId);

    /**
     * Lo que tardo cada pedido terminado, en ticks, en orden. El promedio y el p95 se sacan de esta lista en
     * memoria: son los pedidos de una tienda, no de todas, y calcular un percentil en SQL saldria distinto en H2
     * y en PostgreSQL, que es justo lo que no se quiere de un numero que se publica.
     */
    @Query("""
            select c.tickFin - c.tickInicio from Caso c
            where c.empresa.id = :empresaId
              and (:procesoId is null or c.proceso.id = :procesoId)
              and c.estado = com.facimus.procesos.ejecucion.model.EstadoCaso.TERMINADO
              and c.tickFin is not null
            order by c.tickFin - c.tickInicio
            """)
    List<Integer> ticksDeCiclo(@Param("empresaId") Long empresaId, @Param("procesoId") Long procesoId);

    /** Cuantos casos lleva un proceso: es por donde siguen numerando las tandas de pedidos simulados. */
    long countByEmpresaIdAndProcesoId(Long empresaId, Long procesoId);

    /** Los casos de un proceso con esa referencia: por aqui encuentra su caso un mensaje que llega. */
    List<Caso> porReferencia(@Param("empresaId") Long empresaId, @Param("procesoId") Long procesoId,
            @Param("referencia") String referencia);

    /**
     * D3: bloquea la fila del caso hasta el final de la transaccion (select ... for update). Toda operacion que lo
     * avanza empieza aqui, asi que completar una tarea, recibir un mensaje y mover el reloj se hacen uno detras de
     * otro y no dos a la vez sobre los mismos tokens.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Caso c where c.id = :id and c.empresa.id = :empresaId")
    Optional<Caso> bloquear(@Param("id") Long id, @Param("empresaId") Long empresaId);
}
