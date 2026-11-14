package com.facimus.procesos.ejecucion.model;

/** PR 68: donde esta el temporizador en el diagrama, que es lo que dice que pasa cuando salta. */
public enum ClaseTemporizador {

    /** El evento de inicio temporizado de la version vigente: al saltar abre un caso. No tiene caso propio. */
    INICIO,

    /** Una espera en mitad del flujo: al saltar, el token sigue. */
    INTERMEDIO,

    /** Pegado a una actividad que espera: al saltar saca un token por su salida, interrumpiendola o no. */
    BORDE
}
