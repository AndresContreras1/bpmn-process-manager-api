package com.facimus.procesos.modelado.service;

import com.facimus.procesos.modelado.model.DisposicionBpmn.Geometria;

/**
 * PR 52: la geometria BPMN DI del diagrama de cada proceso, en coordenadas absolutas. Se recalcula sola con cada
 * cambio del proceso; la exportacion a BPMN (PR 53) la lee de aqui.
 */
public interface GeometriaService {

    /** La recalcula desde el modelo y reemplaza la guardada. */
    void recalcular(Long empresaId, Long procesoId);

    /** La guardada, tal como la dejo el ultimo cambio. */
    Geometria obtener(Long empresaId, Long procesoId);
}
