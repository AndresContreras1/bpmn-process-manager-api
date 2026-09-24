package com.facimus.procesos.modelado.service;

import java.util.List;

import com.facimus.procesos.modelado.dto.response.ArcoResponse;

/** HU-11 a HU-13: arcos (flujo entre nodos dentro de un pool). */
public interface ArcoService {

    ArcoResponse crear(Long empresaId, Long usuarioId, Long origenId, Long destinoId, String etiqueta,
            String condicion, boolean porDefecto, int orden);

    ArcoResponse editar(Long empresaId, Long usuarioId, Long arcoId, String etiqueta, String condicion,
            boolean porDefecto, int orden, Long version);

    void eliminar(Long empresaId, Long usuarioId, Long arcoId);

    ArcoResponse obtener(Long empresaId, Long arcoId);

    List<ArcoResponse> listarPorPool(Long empresaId, Long poolId);
}
