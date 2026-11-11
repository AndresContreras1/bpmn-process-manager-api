package com.facimus.procesos.modelado.model;

/**
 * Los eventos que el modelo acepta: donde empieza un proceso, donde termina y los puntos en los que espera o manda un
 * mensaje. PR 55: tambien los de temporizador (un inicio, una espera y uno de borde pegado a una actividad) y el fin
 * que termina el caso entero; los temporizadores se describen y se publican, y corren cuando llegue el calendario.
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
    /** Termina un camino mandando un mensaje. */
    MENSAJE_FIN,
    /** Arranca el proceso en un momento: una fecha, cada cierto tiempo. */
    TEMPORIZADOR_INICIO,
    /** Espera un tiempo, o hasta una fecha, en mitad del flujo. */
    TEMPORIZADOR_INTERMEDIO,
    /** Pegado a una actividad: si el tiempo pasa antes de que termine, el flujo sale por el. */
    TEMPORIZADOR_BORDE,
    /** Termina el caso entero: los demas caminos que sigan vivos se cancelan. */
    FIN_TERMINACION;

    public boolean empiezaElProceso() {
        return this == INICIO || this == MENSAJE_INICIO || this == TEMPORIZADOR_INICIO;
    }

    public boolean terminaElProceso() {
        return this == FIN || this == MENSAJE_FIN || this == FIN_TERMINACION;
    }

    public boolean puedeEnviar() {
        return this == MENSAJE_FIN;
    }

    public boolean puedeRecibir() {
        return this == MENSAJE_INICIO || this == MENSAJE_INTERMEDIO;
    }

    public boolean esTemporizador() {
        return this == TEMPORIZADOR_INICIO || this == TEMPORIZADOR_INTERMEDIO || this == TEMPORIZADOR_BORDE;
    }

    /** Un evento de borde no esta en el flujo: sale de la actividad a la que va pegado, y no le llega nada. */
    public boolean esDeBorde() {
        return this == TEMPORIZADOR_BORDE;
    }
}
