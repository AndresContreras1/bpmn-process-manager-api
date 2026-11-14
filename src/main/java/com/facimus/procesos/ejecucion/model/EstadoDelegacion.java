package com.facimus.procesos.ejecucion.model;

/** PR 70: en que quedo una delegacion. Solo una ACTIVA por tarea; las demas son su historia. */
public enum EstadoDelegacion {
    /** El delegado tiene la tarea. */
    ACTIVA,
    /** El delegado la solto y volvio a quien la delego. */
    DEVUELTA,
    /** La tarea se completo mientras estaba delegada: la completo el delegado, o un administrador por el. */
    COMPLETADA,
    /**
     * Ya no vuelve a quien la delego: alguien la reasigno o la solto a la bandeja del rol, el delegado la solto cuando
     * quien la delego ya no podia tener tareas, o el caso dejo la tarea atras (cancelado, fallido o un borde que la
     * interrumpe).
     */
    ANULADA
}
