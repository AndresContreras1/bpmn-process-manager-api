package com.facimus.procesos.modelado.model;

/**
 * Los eventos que el modelo acepta: donde empieza un proceso, donde termina y los puntos en los que espera o manda un
 * mensaje. Es el subconjunto de BPMN que el proyecto necesita; no hay temporizadores ni eventos de error.
 */
public enum TipoEvento {

    /** Arranca el proceso sin esperar nada. */
    INICIO,
    /** Termina un camino del proceso. */
    FIN,
    /** Arranca el proceso cuando llega un mensaje de otro participante. */
    MENSAJE_INICIO,
    /** Espera un mensaje en mitad del flujo. */
    MENSAJE_INTERMEDIO,
    /** Manda un mensaje y termina el camino. */
    MENSAJE_FIN;

    /** Por donde empieza el proceso: nada llega a un evento de inicio. */
    public boolean empiezaElProceso() {
        return this == INICIO || this == MENSAJE_INICIO;
    }

    /** Por donde termina un camino: de un evento de fin no sale nada. */
    public boolean terminaElProceso() {
        return this == FIN || this == MENSAJE_FIN;
    }
}
