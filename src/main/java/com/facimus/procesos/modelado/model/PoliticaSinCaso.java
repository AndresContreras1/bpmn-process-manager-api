package com.facimus.procesos.modelado.model;

/** Que hacer con un mensaje que llega y no corresponde a ningun caso abierto. */
public enum PoliticaSinCaso {

    /** Se descarta: nadie lo esperaba. */
    DESCARTAR,
    /** Abre un caso nuevo: es el mensaje con el que empieza el proceso. */
    INICIAR_CASO;

    /** Solo uno de los dos abre casos, y aun ese solo si esta anclado a un evento que empieza el proceso. */
    public boolean abreCaso() {
        return this == INICIAR_CASO;
    }
}
