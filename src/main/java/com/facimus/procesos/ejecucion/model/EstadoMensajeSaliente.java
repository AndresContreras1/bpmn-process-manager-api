package com.facimus.procesos.ejecucion.model;

/** En que va un mensaje que el proceso mando. */
public enum EstadoMensajeSaliente {

    /** Escrito en la bandeja de salida, esperando a que el reloj llegue a su tick de entrega. */
    PENDIENTE,
    /** Lo recibio el participante del otro lado. */
    ENTREGADO,
    /** No llego, y lo que pase ahora lo decide el siFalla del mensaje. */
    FALLIDO;

    public boolean estaPendiente() {
        return this == PENDIENTE;
    }
}
