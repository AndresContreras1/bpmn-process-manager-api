package com.facimus.procesos.modelado.service;

import com.facimus.procesos.gestion.service.ModeloBpmn;

/** PR 53: un proceso en BPMN 2.0.2, el formato con el que se lleva a otra herramienta y se trae de vuelta. */
public interface ModeloBpmnService {

    /**
     * El proceso como archivo: la tienda duena recibe su borrador, con la geometria que tiene ahora; una invitada, la
     * version vigente, que es lo unico que ve de el (HU-23).
     */
    ModeloBpmn exportar(Long empresaId, Long procesoId);
}
