package com.facimus.procesos.ejecucion.model;

/** PR 70: en que quedo una delegacion. Solo una ACTIVA por tarea; las demas son su historia. */
public enum EstadoDelegacion {
    /** El delegado tiene la tarea. */
    ACTIVA,
    /** El delegado la solto y volvio a quien la delego. */
    DEVUELTA,
    /** El delegado la completo. */
    COMPLETADA,
    /** Alguien la reasigno o la solto a la bandeja del rol: ya no vuelve a quien la delego. */
    ANULADA
}
