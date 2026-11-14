package com.facimus.procesos.gestion.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.facimus.procesos.common.RepositorioTenant;
import com.facimus.procesos.common.notificaciones.TipoDeNotificacion;
import com.facimus.procesos.gestion.model.PreferenciaNotificacion;

/** PR 63: como quiere cada persona el correo de cada tipo de notificacion. */
public interface PreferenciaNotificacionRepository extends RepositorioTenant<PreferenciaNotificacion> {

    List<PreferenciaNotificacion> findAllByEmpresaIdAndUsuarioId(Long empresaId, Long usuarioId);

    /** Las de un tipo para varias personas: una notificacion a muchas lee sus preferencias de una vez. */
    List<PreferenciaNotificacion> findAllByEmpresaIdAndTipoAndUsuarioIdIn(Long empresaId, TipoDeNotificacion tipo,
            Collection<Long> usuarioIds);

    @Modifying
    @Query("delete from PreferenciaNotificacion p where p.empresa.id = :empresaId and p.usuarioId = :usuarioId")
    int borrarLasDe(@Param("empresaId") Long empresaId, @Param("usuarioId") Long usuarioId);
}
