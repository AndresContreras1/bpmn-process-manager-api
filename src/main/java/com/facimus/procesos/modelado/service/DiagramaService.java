package com.facimus.procesos.modelado.service;

import com.facimus.procesos.modelado.dto.response.DiagramaResponse;

/** El diagrama completo de un proceso en una sola respuesta, para dibujarlo sin una peticion por pool o lane. */
public interface DiagramaService {

    DiagramaResponse obtener(Long empresaId, Long procesoId);
}
