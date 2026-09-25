package com.facimus.procesos.ejecucion.model;

/**
 * En que va un paso por un nodo. Los tokens vivos de un caso son sus filas PENDIENTE y EN_ESPERA: el motor procesa
 * las primeras hasta que no queda ninguna, y las segundas esperan a algo de fuera.
 */
public enum EstadoActividadCaso {

    /** Lista para que el motor la procese en esta misma transaccion. */
    PENDIENTE,
    /** Espera a una persona, a un mensaje o a que lleguen los demas tokens de un join. */
    EN_ESPERA,
    COMPLETADA,
    /** Un envio que fallo y no se pudo manejar. */
    FALLIDA,
    /** El caso se cancelo o termino mientras este token seguia vivo. */
    OMITIDA;

    /** Un token vivo es el que todavia puede mover el caso. */
    public boolean estaVivo() {
        return this == PENDIENTE || this == EN_ESPERA;
    }
}
