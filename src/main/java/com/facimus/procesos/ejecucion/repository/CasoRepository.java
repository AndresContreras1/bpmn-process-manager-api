package com.facimus.procesos.ejecucion.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.facimus.procesos.common.RepositorioTenant;
import com.facimus.procesos.ejecucion.dto.response.CasosPorEstadoResponse;
import com.facimus.procesos.ejecucion.dto.response.CumplimientoDeCasosResponse;
import com.facimus.procesos.ejecucion.model.Caso;
import com.facimus.procesos.ejecucion.model.EstadoCaso;

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
              and c.versionProceso.tipo = com.facimus.procesos.gestion.model.TipoVersion.PUBLICADA
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
              and c.versionProceso.tipo = com.facimus.procesos.gestion.model.TipoVersion.PUBLICADA
            order by c.tickFin - c.tickInicio
            """)
    List<Integer> ticksDeCiclo(@Param("empresaId") Long empresaId, @Param("procesoId") Long procesoId);

    /**
     * PR 71: cuantos casos con plazo terminaron a tiempo y cuantos tarde, terminados despues de vencer o abiertos
     * pasado su plazo. Uno cancelado o fallido no se mide, ni una prueba del borrador, ni uno de un proceso que quien
     * pregunta no puede ver (PR 60).
     */
    @Query("""
            select new com.facimus.procesos.ejecucion.dto.response.CumplimientoDeCasosResponse(
                    coalesce(sum(case when c.estado = com.facimus.procesos.ejecucion.model.EstadoCaso.TERMINADO
                                       and c.vencimiento.estado <> com.facimus.procesos.ejecucion.model.EstadoPlazo.VENCIDA then 1 else 0 end), 0),
                    coalesce(sum(case when c.vencimiento.estado = com.facimus.procesos.ejecucion.model.EstadoPlazo.VENCIDA then 1 else 0 end), 0))
            from Caso c join c.proceso p
            where c.empresa.id = :empresaId
              and (:procesoId is null or p.id = :procesoId)
              and c.vencimiento.estado is not null
              and (c.estado = com.facimus.procesos.ejecucion.model.EstadoCaso.TERMINADO
                   or c.estado in (com.facimus.procesos.ejecucion.model.EstadoCaso.ABIERTO, com.facimus.procesos.ejecucion.model.EstadoCaso.ERROR) and c.vencimiento.estado = com.facimus.procesos.ejecucion.model.EstadoPlazo.VENCIDA)
              and c.versionProceso.tipo = com.facimus.procesos.gestion.model.TipoVersion.PUBLICADA
              and (:#{#visible.todos()} = true
                   or (p.carpetaId is null or p.carpetaId not in :#{#visible.carpetasOcultas()}
                       or p.id in :#{#visible.procesosVisibles()})
                      and p.id not in :#{#visible.procesosOcultos()})
            """)
    CumplimientoDeCasosResponse cumplimientoDeCasos(@Param("empresaId") Long empresaId,
            @Param("procesoId") Long procesoId, @Param("visible") ProcesosVisibles visible);

    /** Cuantos hay en un estado en toda la instalacion: es lo que publica el medidor de Actuator. */
    @Query("""
            select count(c) from Caso c
            where c.estado = :estado and c.versionProceso.tipo = com.facimus.procesos.gestion.model.TipoVersion.PUBLICADA
            """)
    long countByEstado(@Param("estado") EstadoCaso estado);

    /** Cuantos casos lleva un proceso: es por donde siguen numerando las tandas de pedidos simulados. */
    long countByEmpresaIdAndProcesoId(Long empresaId, Long procesoId);

    /**
     * Los casos de un proceso con esa referencia: por aqui encuentra su caso un mensaje que llega. PR 68: los de una
     * prueba del borrador no estan; un mensaje de fuera es de los pedidos de verdad, y la prueba corre en otro grafo.
     */
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

    /**
     * PR 71: los casos de produccion de todas las tiendas cuyo plazo ya tendria que haber cambiado, por id desde el
     * ultimo del lote anterior. Uno en error sigue contando: el tiempo pasa igual mientras alguien lo rescata.
     */
    @Query("""
            select new com.facimus.procesos.ejecucion.repository.PlazoQueCambia(c.id, c.empresa.id)
            from Caso c
            where c.id > :despuesDe
              and c.estado in (com.facimus.procesos.ejecucion.model.EstadoCaso.ABIERTO, com.facimus.procesos.ejecucion.model.EstadoCaso.ERROR)
              and (c.vencimiento.estado = com.facimus.procesos.ejecucion.model.EstadoPlazo.A_TIEMPO
                       and c.vencimiento.avisoEn <= :ahora
                   or c.vencimiento.estado = com.facimus.procesos.ejecucion.model.EstadoPlazo.POR_VENCER
                       and c.vencimiento.venceEn <= :ahora)
            order by c.id
            """)
    List<PlazoQueCambia> plazosQueCambianEnProduccion(@Param("ahora") LocalDateTime ahora,
            @Param("despuesDe") Long despuesDe, Pageable lote);

    /** PR 71: lo mismo con los de prueba de una tienda, en ese tick. */
    @Query("""
            select new com.facimus.procesos.ejecucion.repository.PlazoQueCambia(c.id, c.empresa.id)
            from Caso c
            where c.empresa.id = :empresaId
              and c.estado in (com.facimus.procesos.ejecucion.model.EstadoCaso.ABIERTO, com.facimus.procesos.ejecucion.model.EstadoCaso.ERROR)
              and (c.vencimiento.estado = com.facimus.procesos.ejecucion.model.EstadoPlazo.A_TIEMPO
                       and c.vencimiento.avisoTick <= :tick
                   or c.vencimiento.estado = com.facimus.procesos.ejecucion.model.EstadoPlazo.POR_VENCER
                       and c.vencimiento.venceTick <= :tick)
            order by c.id
            """)
    List<PlazoQueCambia> plazosQueCambianEnPrueba(@Param("empresaId") Long empresaId, @Param("tick") int tick);

    /**
     * PR 71: mueve el plazo de un caso que sigue abierto o en error, solo si sigue en el estado que se leyo; como el de
     * una tarea, dos repasos a la vez lo mueven una sola vez, y sin subir la version.
     *
     * @return 1 si lo movio, 0 si otro ya lo movio, se cerro o no es de esta tienda
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(nativeQuery = true, value = """
            update casos set estado_plazo = :nuevo
            where id = :id and empresa_id = :empresaId and estado in ('ABIERTO', 'ERROR') and estado_plazo = :anterior
            """)
    int moverPlazo(@Param("id") Long id, @Param("empresaId") Long empresaId, @Param("anterior") String anterior,
            @Param("nuevo") String nuevo);
}
