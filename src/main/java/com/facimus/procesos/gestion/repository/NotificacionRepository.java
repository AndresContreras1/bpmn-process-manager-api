package com.facimus.procesos.gestion.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.facimus.procesos.common.RepositorioTenant;
import com.facimus.procesos.gestion.model.Notificacion;

/** PR 63: las notificaciones de cada persona. Toda consulta va por la tienda y por la persona. */
public interface NotificacionRepository extends RepositorioTenant<Notificacion> {

    Optional<Notificacion> findByIdAndEmpresaIdAndUsuarioId(Long id, Long empresaId, Long usuarioId);

    Page<Notificacion> findAllByEmpresaIdAndUsuarioIdOrderByCreadaEnDescIdDesc(Long empresaId, Long usuarioId,
            Pageable pagina);

    Page<Notificacion> findAllByEmpresaIdAndUsuarioIdAndLeidaEnIsNullOrderByCreadaEnDescIdDesc(Long empresaId,
            Long usuarioId, Pageable pagina);

    long countByEmpresaIdAndUsuarioIdAndLeidaEnIsNull(Long empresaId, Long usuarioId);

    /** Las de un correo que espera en la cola: las que siguen sin resolver. */
    List<Notificacion> findAllByEmpresaIdAndUsuarioIdAndIdInAndCorreoEnIsNullOrderByCreadaEnAscIdAsc(Long empresaId,
            Long usuarioId, Collection<Long> ids);

    @Modifying
    @Query("""
            update Notificacion n set n.leidaEn = :ahora
            where n.empresa.id = :empresaId and n.usuarioId = :usuarioId and n.leidaEn is null
            """)
    int marcarLeidas(@Param("empresaId") Long empresaId, @Param("usuarioId") Long usuarioId,
            @Param("ahora") LocalDateTime ahora);

    @Modifying
    @Query("""
            update Notificacion n set n.correoEn = :ahora
            where n.empresa.id = :empresaId and n.usuarioId = :usuarioId and n.id in :ids and n.correoEn is null
            """)
    int resolverCorreo(@Param("empresaId") Long empresaId, @Param("usuarioId") Long usuarioId,
            @Param("ids") Collection<Long> ids, @Param("ahora") LocalDateTime ahora);

    /** Las que esperan el resumen del dia, de cada persona de cada tienda: la tienda, la persona y la notificacion. */
    @Query("""
            select n.empresa.id, n.usuarioId, n.id from Notificacion n
            where n.envio = com.facimus.procesos.common.notificaciones.EnvioPorCorreo.RESUMEN_DIARIO
              and n.correoEn is null
            order by n.empresa.id, n.usuarioId, n.id
            """)
    List<Object[]> pendientesDelResumen();

    /** Lo leido hace mas de lo que se guarda. Lo que no se leyo se queda: nadie se entero todavia. */
    @Modifying
    @Query("delete from Notificacion n where n.leidaEn < :antes")
    int borrarLeidasAntesDe(@Param("antes") LocalDateTime antes);

    /** Las de alguien que se anonimiza: eran solo suyas. */
    @Modifying
    @Query("delete from Notificacion n where n.empresa.id = :empresaId and n.usuarioId = :usuarioId")
    int borrarLasDe(@Param("empresaId") Long empresaId, @Param("usuarioId") Long usuarioId);
}
