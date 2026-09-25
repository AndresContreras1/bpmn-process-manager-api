package com.facimus.procesos.ejecucion.model;

/**
 * Lo que se anota en la bitacora de un caso. Cada linea explica por que el caso esta donde esta, que es lo que se
 * lee cuando algo no salio como se esperaba.
 */
public enum TipoEventoCaso {

    CASO_ABIERTO,
    /** Un token llego a un nodo y lo paso. */
    NODO_ACTIVADO,
    TAREA_CREADA,
    TAREA_COMPLETADA,
    /** Un gateway eligio por donde seguir, con la condicion que tomo. */
    GATEWAY_DECIDIO,
    MENSAJE_ENVIADO,
    MENSAJE_RECIBIDO,
    ENVIO_FALLIDO,
    /** Ninguna condicion se cumplio y el gateway no tenia salida por defecto: el caso queda en ERROR. */
    SIN_CAMINO,
    /** Una condicion pregunto por una variable que el caso no tiene. */
    VARIABLE_AUSENTE,
    CASO_TERMINADO,
    CASO_CANCELADO
}
