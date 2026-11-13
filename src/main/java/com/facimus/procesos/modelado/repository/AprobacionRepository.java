package com.facimus.procesos.modelado.repository;

import java.util.Collection;
import java.util.List;

import com.facimus.procesos.common.RepositorioTenant;
import com.facimus.procesos.modelado.model.Aprobacion;
import com.facimus.procesos.modelado.model.Decision;

/** PR 64: lo que decidio cada aprobador de una solicitud. */
public interface AprobacionRepository extends RepositorioTenant<Aprobacion> {

    /** Las de unas solicitudes, de la mas vieja a la mas nueva, para responderlas de una vez. */
    List<Aprobacion> findAllByEmpresaIdAndSolicitudIdInOrderByFechaAscIdAsc(Long empresaId,
            Collection<Long> solicitudes);

    boolean existsByEmpresaIdAndSolicitudIdAndAprobadorId(Long empresaId, Long solicitudId, Long aprobadorId);

    long countByEmpresaIdAndSolicitudIdAndDecision(Long empresaId, Long solicitudId, Decision decision);
}
