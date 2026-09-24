package com.facimus.procesos.modelado.service.impl;

import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.modelado.model.Lane;
import com.facimus.procesos.modelado.model.NodoFlujo;
import com.facimus.procesos.modelado.repository.ArcoRepository;
import com.facimus.procesos.modelado.repository.LaneRepository;
import com.facimus.procesos.modelado.repository.MensajeRepository;

/**
 * R-41: a donde puede mudarse un nodo. Un nodo se arrastra a cualquier lane de su proceso mientras este suelto,
 * pero en cuanto tiene arcos o mensajes anclados se queda en su pool, porque un arco no cruza pools y un mensaje
 * se ancla al nodo del pool de su lado. Las tres clases de nodo se mueven igual, asi que la regla vive aqui.
 */
final class ReglasDeNodos {

    static final String OTRO_PROCESO = "Un nodo solo se mueve a una lane del mismo proceso.";
    static final String CON_ARCOS = "El nodo tiene arcos y solo puede moverse a una lane del mismo pool.";
    static final String CON_MENSAJES = "El nodo tiene mensajes anclados y solo puede moverse a una lane del mismo "
            + "pool.";

    private ReglasDeNodos() {
    }

    /** La lane a la que se muda el nodo; sin pedir mudanza, la suya. */
    static Lane mudanza(Long empresaId, NodoFlujo nodo, Long laneId, LaneRepository laneRepository,
            ArcoRepository arcoRepository, MensajeRepository mensajeRepository) {
        Lane actual = nodo.getLane();
        if (laneId == null || laneId.equals(actual.getId())) {
            return actual;
        }
        Lane destino = laneRepository.findByIdAndEmpresaId(laneId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Lane no encontrada."));
        if (!destino.getPool().getProceso().getId().equals(actual.getPool().getProceso().getId())) {
            throw new ReglaNegocioException(OTRO_PROCESO);
        }
        if (!destino.getPool().getId().equals(actual.getPool().getId())) {
            if (arcoRepository.tieneArcos(nodo.getId(), empresaId)) {
                throw new ReglaNegocioException(CON_ARCOS);
            }
            if (mensajeRepository.tieneMensajesAnclados(nodo.getId(), empresaId)) {
                throw new ReglaNegocioException(CON_MENSAJES);
            }
        }
        return destino;
    }
}
