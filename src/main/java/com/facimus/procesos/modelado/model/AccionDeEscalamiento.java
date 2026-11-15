package com.facimus.procesos.modelado.model;

/**
 * PR 71: lo que hace un temporizador de borde que no interrumpe cuando salta sobre una tarea, ademas de abrir su
 * camino. El supervisor es la persona que dice el evento, o el dueno del proceso si no dice ninguna.
 */
public enum AccionDeEscalamiento {
    /** Le avisa a quien tiene la tarea y a su supervisor; la tarea sigue donde estaba. */
    AVISAR,
    /** Le pasa la tarea al supervisor y les avisa a los dos. */
    REASIGNAR
}
