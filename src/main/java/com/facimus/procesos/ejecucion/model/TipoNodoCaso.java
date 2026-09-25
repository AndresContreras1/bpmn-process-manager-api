package com.facimus.procesos.ejecucion.model;

/**
 * Que clase de nodo recorrio un paso del caso. Es el mismo discriminador de NodoFlujo, copiado a la fila: la
 * instantanea de la version puede ya no coincidir con el modelo vivo, y lo que el caso recorrio no se reescribe.
 */
public enum TipoNodoCaso {
    ACTIVIDAD,
    GATEWAY,
    EVENTO
}
