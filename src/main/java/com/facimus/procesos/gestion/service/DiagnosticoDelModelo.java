package com.facimus.procesos.gestion.service;

import java.util.List;

import com.facimus.procesos.gestion.dto.response.ProcesoResponse;

/**
 * Lo que impide publicar un proceso. El catalogo de diagnostico vive en modelado; gestion solo necesita saber si hay
 * errores y cuales, para no publicar un diagrama que no se puede ejecutar (R-44).
 */
public interface DiagnosticoDelModelo {

    /**
     * Los errores del diagrama, ya redactados. Vacia cuando el proceso se puede publicar. El proceso llega leido,
     * igual que en InstantaneaDelModelo: quien pregunta acaba de cargarlo y ya comprobo que puede tocarlo.
     */
    List<String> errores(Long empresaId, ProcesoResponse proceso);
}
