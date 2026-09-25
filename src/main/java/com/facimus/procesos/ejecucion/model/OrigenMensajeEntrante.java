package com.facimus.procesos.ejecucion.model;

import com.facimus.procesos.modelado.model.Integracion;

/** De donde salio un mensaje que llego al proceso. Nada de esto es real: los socios son simulados (D7). */
public enum OrigenMensajeEntrante {

    /** Lo mando alguien por la API, que es como se prueba un proceso sin socios. */
    MANUAL,
    CLIENTE_SIMULADO,
    SIMULADOR_PAGOS,
    SIMULADOR_TRANSPORTE,
    SIMULADOR_NOTIFICACIONES;

    /**
     * De que socio viene una respuesta, segun la clase de participante que la manda. Un participante sin socio
     * propio se apunta como cliente: es el unico de los cinco que describe a alguien que simplemente contesta.
     */
    public static OrigenMensajeEntrante delSocio(Integracion integracion) {
        return switch (integracion) {
            case PAGOS -> SIMULADOR_PAGOS;
            case TRANSPORTE -> SIMULADOR_TRANSPORTE;
            case NOTIFICACIONES -> SIMULADOR_NOTIFICACIONES;
            case CLIENTE, NINGUNA -> CLIENTE_SIMULADO;
        };
    }
}
