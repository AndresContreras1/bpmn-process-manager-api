package com.facimus.procesos.gestion.service;

import java.util.List;

import com.facimus.procesos.gestion.dto.response.RolDeUsuarioResponse;

/**
 * D13: a que roles de proceso pertenece cada persona. Sirve para que alguien pida solo su bandeja; no reparte
 * permisos, que los sigue decidiendo el rol de acceso.
 */
public interface MembresiaRolService {

    List<RolDeUsuarioResponse> rolesDe(Long empresaId, Long usuarioId);

    /**
     * Reemplaza los roles de una persona por los que llegan. Una lista vacia la deja sin ninguno, que es como se
     * quita el ultimo.
     */
    List<RolDeUsuarioResponse> reemplazar(Long empresaId, Long autorId, Long usuarioId, List<Long> rolesProcesoIds);

    /** Los ids de sus roles, que es lo unico que la bandeja propia necesita saber. */
    List<Long> idsDeLosRolesDe(Long empresaId, Long usuarioId);
}
