package com.facimus.procesos.ejecucion.model;

/** PR 68: en que va un temporizador armado. */
public enum EstadoTemporizador {

    /** Armado, esperando su hora. Un ciclo vuelve aqui despues de cada salto mientras le queden. */
    PROGRAMADO,

    /** Ya salto, y no le queda otra vez. */
    DISPARADO,

    /** Su caso termino o se cancelo, o la actividad que lo armo ya no espera: no saltara. */
    CANCELADO,

    /** Al saltar no pudo hacer lo que tocaba (su version ya no corre, su nodo no esta): queda con el error. */
    FALLIDO
}
