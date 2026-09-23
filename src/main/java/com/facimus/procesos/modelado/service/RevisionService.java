package com.facimus.procesos.modelado.service;

import com.facimus.procesos.modelado.dto.response.RevisionResponse;

/** Revision del diagrama de un proceso con ayuda de un modelo de lenguaje. */
public interface RevisionService {

    /** Describe el diagrama, se lo pasa al revisor y devuelve sus hallazgos. */
    RevisionResponse revisar(Long empresaId, Long procesoId);
}
