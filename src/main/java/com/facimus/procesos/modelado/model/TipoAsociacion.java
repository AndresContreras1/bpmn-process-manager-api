package com.facimus.procesos.modelado.model;

/** PR 55: que une una asociacion. */
public enum TipoAsociacion {
    /** Un artefacto con otro elemento, como una anotacion con la tarea que explica. */
    ASOCIACION,
    /** Un dato que entra a una tarea. */
    ENTRADA_DATOS,
    /** Un dato que sale de una tarea. */
    SALIDA_DATOS
}
