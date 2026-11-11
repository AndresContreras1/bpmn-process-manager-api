package com.facimus.procesos.modelado.service;

import java.util.List;

import com.facimus.procesos.modelado.dto.response.AsociacionResponse;
import com.facimus.procesos.modelado.model.DireccionAsociacion;
import com.facimus.procesos.modelado.model.TipoAsociacion;

/**
 * PR 55: lo que une un artefacto con otro elemento. Los extremos se nombran por su bpmn_id, porque pueden ser nodos,
 * flujos o artefactos.
 */
public interface AsociacionService {

    AsociacionResponse crear(Long empresaId, Long usuarioId, Long procesoId, TipoAsociacion tipo, String origen,
            String destino, DireccionAsociacion direccion);

    void eliminar(Long empresaId, Long usuarioId, Long asociacionId);

    List<AsociacionResponse> listar(Long empresaId, Long procesoId);
}
