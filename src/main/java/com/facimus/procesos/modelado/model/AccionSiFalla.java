package com.facimus.procesos.modelado.model;

/** Que hace el proceso cuando el envio de un mensaje no llega a su destino. */
public enum AccionSiFalla {

    /** El proceso sigue por donde iba: el mensaje no era imprescindible. */
    CONTINUAR,
    /** El proceso se desvia a una actividad que atiende el problema. */
    MANEJAR_ERROR,
    /** El proceso termina: sin ese mensaje no tiene sentido continuar. */
    FINALIZAR;

    /** Solo el desvio necesita saber a que actividad va. */
    public boolean necesitaActividad() {
        return this == MANEJAR_ERROR;
    }
}
