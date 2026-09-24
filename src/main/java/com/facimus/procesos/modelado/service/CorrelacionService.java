package com.facimus.procesos.modelado.service;

import com.facimus.procesos.modelado.dto.response.CorrelacionResponse;
import com.facimus.procesos.modelado.model.PoliticaSinCaso;

/** HU-28: correlacion de mensajes. */
public interface CorrelacionService {

    /** Crea la clave del mensaje, o la reemplaza si ya tiene una y el cliente manda la version que leyo. */
    CorrelacionResponse definir(Long empresaId, Long usuarioId, Long mensajeId, String criterio,
            String campo, PoliticaSinCaso sinCaso, Long version);

    CorrelacionResponse obtener(Long empresaId, Long mensajeId);
}
