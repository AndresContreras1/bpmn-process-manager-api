package com.facimus.procesos.ejecucion.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.facimus.procesos.common.RepositorioTenant;
import com.facimus.procesos.ejecucion.model.ClaseTemporizador;
import com.facimus.procesos.ejecucion.model.EstadoTemporizador;
import com.facimus.procesos.ejecucion.model.Temporizador;

/**
 * PR 68: los temporizadores armados. Los vencidos se buscan sin bloquear, y cada uno se toma despues en su propia
 * transaccion con {@code FOR UPDATE SKIP LOCKED}, como la cola de trabajos (D34): dos instancias que buscan a la vez
 * encuentran el mismo, pero solo una lo toma.
 */
public interface TemporizadorRepository extends RepositorioTenant<Temporizador> {

    /** Lo que armo un caso, entero o solo lo que sigue armado. */
    List<Temporizador> findAllByCasoIdAndEmpresaIdOrderByIdAsc(Long casoId, Long empresaId);

    List<Temporizador> findAllByCasoIdAndEmpresaIdAndEstadoOrderByIdAsc(Long casoId, Long empresaId,
            EstadoTemporizador estado);

    /**
     * Los inicios temporizados de un proceso, del mas nuevo al mas viejo y por paginas: cada publicacion arma los
     * suyos y deja cancelados los de la anterior, asi que la lista crece con el proceso.
     */
    Page<Temporizador> findAllByProcesoIdAndEmpresaIdAndClaseOrderByIdDesc(Long procesoId, Long empresaId,
            ClaseTemporizador clase, Pageable pagina);

    List<Temporizador> findAllByProcesoIdAndEmpresaIdAndClaseAndEstadoOrderByIdAsc(Long procesoId, Long empresaId,
            ClaseTemporizador clase, EstadoTemporizador estado);

    /**
     * Los de produccion vencidos de todas las tiendas, del que vencio antes al que vencio despues. Un caso en error no
     * dispara los suyos: siguen vencidos y saltan en la primera pasada despues de rescatarlo.
     */
    @Query("""
            select new com.facimus.procesos.ejecucion.repository.TemporizadorVencido(t.id, t.empresa.id)
            from Temporizador t
            where t.estado = com.facimus.procesos.ejecucion.model.EstadoTemporizador.PROGRAMADO
              and t.modo = com.facimus.procesos.ejecucion.model.ModoCaso.PRODUCCION
              and t.venceEn <= :ahora
              and (t.casoId is null or exists (select c.id from Caso c where c.id = t.casoId
                   and c.estado = com.facimus.procesos.ejecucion.model.EstadoCaso.ABIERTO))
            order by t.venceEn, t.id
            """)
    List<TemporizadorVencido> vencidosEnProduccion(@Param("ahora") LocalDateTime ahora, Pageable lote);

    /** Los de prueba de una tienda que vencen en ese tick o antes, en el orden en que vencen. */
    @Query("""
            select new com.facimus.procesos.ejecucion.repository.TemporizadorVencido(t.id, t.empresa.id)
            from Temporizador t
            where t.empresa.id = :empresaId
              and t.estado = com.facimus.procesos.ejecucion.model.EstadoTemporizador.PROGRAMADO
              and t.modo = com.facimus.procesos.ejecucion.model.ModoCaso.PRUEBA
              and t.venceTick <= :tick
              and (t.casoId is null or exists (select c.id from Caso c where c.id = t.casoId
                   and c.estado = com.facimus.procesos.ejecucion.model.EstadoCaso.ABIERTO))
            order by t.venceTick, t.id
            """)
    List<TemporizadorVencido> vencidosEnPrueba(@Param("empresaId") Long empresaId, @Param("tick") int tick);

    /** El caso de un temporizador, sin cargarlo: quien lo dispara bloquea el caso antes de tocar el temporizador. */
    @Query("select t.casoId from Temporizador t where t.id = :id and t.empresa.id = :empresaId")
    Optional<Long> casoDe(@Param("id") Long id, @Param("empresaId") Long empresaId);

    /**
     * Toma un temporizador de produccion que sigue armado y vencido. Si otra transaccion lo tiene tomado, no espera:
     * no lo devuelve, y esa transaccion es la que lo dispara.
     */
    @Query(nativeQuery = true, value = """
            select * from temporizadores
            where id = :id and empresa_id = :empresaId and estado = 'PROGRAMADO' and modo = 'PRODUCCION'
              and vence_en <= :ahora
            for update skip locked
            """)
    Optional<Temporizador> tomarEnProduccion(@Param("id") Long id, @Param("empresaId") Long empresaId,
            @Param("ahora") LocalDateTime ahora);

    /** Lo mismo con uno de prueba, vencido en ese tick. */
    @Query(nativeQuery = true, value = """
            select * from temporizadores
            where id = :id and empresa_id = :empresaId and estado = 'PROGRAMADO' and modo = 'PRUEBA'
              and vence_tick <= :tick
            for update skip locked
            """)
    Optional<Temporizador> tomarEnPrueba(@Param("id") Long id, @Param("empresaId") Long empresaId,
            @Param("tick") int tick);
}
