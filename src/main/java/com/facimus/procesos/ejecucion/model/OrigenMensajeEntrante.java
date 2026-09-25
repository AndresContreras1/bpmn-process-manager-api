package com.facimus.procesos.ejecucion.model;

/** De donde salio un mensaje que llego al proceso. Nada de esto es real: los socios son simulados (D7). */
public enum OrigenMensajeEntrante {

    /** Lo mando alguien por la API, que es como se prueba un proceso sin socios. */
    MANUAL,
    CLIENTE_SIMULADO,
    SIMULADOR_PAGOS,
    SIMULADOR_TRANSPORTE,
    SIMULADOR_NOTIFICACIONES
}
