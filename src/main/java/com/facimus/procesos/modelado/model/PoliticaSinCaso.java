package com.facimus.procesos.modelado.model;

/** Que hacer con un mensaje que llega y no corresponde a ningun caso abierto. */
public enum PoliticaSinCaso {

    /** Se descarta: nadie lo esperaba. */
    DESCARTAR,
    /** Abre un caso nuevo: es el mensaje con el que empieza el proceso. */
    INICIAR_CASO
}
