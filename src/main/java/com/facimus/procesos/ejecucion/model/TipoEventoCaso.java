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
    CASO_CANCELADO,
    /** PR 68: un temporizador se armo, y dice cuando vence. */
    TEMPORIZADOR_PROGRAMADO,
    /** PR 68: un temporizador salto. */
    TEMPORIZADOR_DISPARADO,
    /** PR 70: la regla de la tarea se la dio a una persona al crearse. */
    TAREA_ASIGNADA,
    /** PR 70: alguien de su rol la tomo. */
    TAREA_RECLAMADA,
    /** PR 70: quien la tenia la solto: vuelve a la bandeja del rol, o a quien la delego. */
    TAREA_LIBERADA,
    /** PR 70: paso a otra persona, con su motivo. */
    TAREA_REASIGNADA,
    /** PR 70: quien la tenia se la paso a otra persona por un tiempo, con su motivo; le vuelve si la suelta. */
    TAREA_DELEGADA,
    /** PR 71: una tarea, o el caso, entro en el ultimo cuarto de su plazo. */
    PLAZO_POR_VENCER,
    /** PR 71: una tarea, o el caso, paso su plazo. */
    PLAZO_VENCIDO,
    /** PR 71: un temporizador de borde que no interrumpe escalo la tarea: avisando, o pasandosela al supervisor. */
    TAREA_ESCALADA
}
