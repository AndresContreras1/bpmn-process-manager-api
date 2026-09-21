package com.facimus.procesos.modelado.service;

import com.facimus.procesos.modelado.dto.response.CorrelacionResponse;

/** HU-28: correlacion de mensajes. */
public interface CorrelacionService {

    CorrelacionResponse definir(Long empresaId, Long mensajeId, String criterio);

    CorrelacionResponse obtener(Long empresaId, Long mensajeId);
}
