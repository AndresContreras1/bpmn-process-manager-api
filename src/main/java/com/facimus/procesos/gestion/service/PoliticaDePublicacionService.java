package com.facimus.procesos.gestion.service;

import java.util.Optional;

import com.facimus.procesos.gestion.dto.response.PoliticaDePublicacionResponse;

/**
 * PR 64: como publica una tienda (D32). Publicar directo es lo de siempre, y lo que tiene la que nunca eligio otra
 * cosa; con aprobacion, publicar pasa por una solicitud que otras personas aprueban.
 */
public interface PoliticaDePublicacionService {

    /** La politica de la tienda; la de por defecto, publicar directo, si nunca se guardo ninguna. */
    PoliticaDePublicacionResponse obtener(Long empresaId);

    /** Solo el administrador de la tienda la cambia; queda en la bitacora. */
    PoliticaDePublicacionResponse guardar(Long empresaId, Long usuarioId, DatosDePolitica datos);

    /** Si la tienda publica con aprobacion: entonces publicar directo responde 409, tambien al administrador. */
    boolean conAprobacion(Long empresaId);

    /**
     * Lo que la politica pide a una solicitud de ese proceso que pide esa persona; vacio si la tienda publica directo.
     * Quien pide nunca esta entre los aprobadores.
     */
    Optional<ReglaDeAprobacion> reglaPara(Long empresaId, Long procesoId, Long solicitanteId);
}
