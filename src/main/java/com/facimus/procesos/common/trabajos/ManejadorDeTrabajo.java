package com.facimus.procesos.common.trabajos;

/**
 * Quien sabe hacer los trabajos de un tipo. Cada modulo registra los suyos como beans; la cola los busca por tipo.
 * Lanzar una excepcion es fallar: la cola lo vuelve a intentar mas tarde, o lo da por fallido si ya no le quedan
 * intentos. Por eso un manejador tiene que poder correr dos veces el mismo trabajo sin hacerlo dos veces.
 */
public interface ManejadorDeTrabajo {

    /** El tipo de trabajo que atiende; uno solo por tipo. */
    String tipo();

    void ejecutar(Trabajo trabajo);
}
