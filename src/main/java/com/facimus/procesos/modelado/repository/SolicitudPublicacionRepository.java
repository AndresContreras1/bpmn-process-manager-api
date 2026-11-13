package com.facimus.procesos.modelado.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.facimus.procesos.common.RepositorioTenant;
import com.facimus.procesos.modelado.model.EstadoSolicitud;
import com.facimus.procesos.modelado.model.SolicitudPublicacion;

import jakarta.persistence.LockModeType;

/** PR 64: las solicitudes de revision de los procesos de una tienda. */
public interface SolicitudPublicacionRepository extends RepositorioTenant<SolicitudPublicacion>,
        JpaSpecificationExecutor<SolicitudPublicacion> {

    boolean existsByProcesoIdAndEmpresaIdAndEstado(Long procesoId, Long empresaId, EstadoSolicitud estado);

    /** Dos decisiones sobre la misma solicitud se toman de a una: si no, las dos contarian las aprobaciones de antes. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SolicitudPublicacion s where s.id = :id and s.empresa.id = :empresaId")
    Optional<SolicitudPublicacion> bloquear(@Param("id") Long id, @Param("empresaId") Long empresaId);

    Page<SolicitudPublicacion> findAllByProcesoIdAndEmpresaId(Long procesoId, Long empresaId, Pageable pagina);

    Page<SolicitudPublicacion> findAllByProcesoIdAndEmpresaIdAndEstado(Long procesoId, Long empresaId,
            EstadoSolicitud estado, Pageable pagina);

    /** Las abiertas en las que esa persona es aprobadora y todavia no decidio: su bandeja "por aprobar". */
    @Query(value = """
            select s.id from solicitudes_publicacion s
            where s.empresa_id = :empresaId and s.estado = 'ABIERTA' and :usuarioId = any(s.aprobadores)
              and not exists (select 1 from aprobaciones a where a.solicitud_id = s.id and a.aprobador_id = :usuarioId)
            """, nativeQuery = true)
    List<Long> pendientesDe(@Param("empresaId") Long empresaId, @Param("usuarioId") Long usuarioId);

    List<SolicitudPublicacion> findAllByEmpresaIdAndSolicitanteIdAndEstado(Long empresaId, Long solicitanteId,
            EstadoSolicitud estado);

    /** La abierta de un proceso, que es una como mucho, para retirarla cuando el proceso se da de baja. */
    List<SolicitudPublicacion> findAllByEmpresaIdAndProcesoIdAndEstado(Long empresaId, Long procesoId,
            EstadoSolicitud estado);
}
