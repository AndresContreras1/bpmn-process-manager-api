package com.facimus.procesos.modelado.service;

import com.facimus.procesos.modelado.dto.response.DiagnosticoResponse;

/**
 * HU-10.3, HU-13.3, HU-14.3, HU-15.3, HU-16.3, HU-25.5, HU-27.4 y HU-28.3: lo que el diagrama tiene mal, revisado
 * contra las reglas de modelado. Es determinista, gratis y sin servicios externos, al reves que la revision con IA:
 * el mismo diagrama devuelve siempre los mismos hallazgos.
 */
public interface DiagnosticoService {

    /**
     * Los hallazgos del diagrama. Con {@code sinElemento} escrito TIPO:id se revisa, en su lugar, el diagrama que
     * quedaria si ese elemento se borrara, para ver que se rompe antes de borrarlo.
     */
    DiagnosticoResponse diagnosticar(Long empresaId, Long procesoId, String sinElemento);
}
