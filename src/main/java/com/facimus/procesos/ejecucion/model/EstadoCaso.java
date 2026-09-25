package com.facimus.procesos.ejecucion.model;

/** Por donde puede pasar un caso. Solo ABIERTO tiene tokens vivos; de los otros cuatro no se sale, salvo de ERROR. */
public enum EstadoCaso {

    /** Corriendo: tiene al menos un token vivo, esperando a una persona, a un mensaje o a un join. */
    ABIERTO,
    /** El ultimo token murio en un evento de fin. */
    TERMINADO,
    /** Alguien lo cerro a mano antes de tiempo. */
    CANCELADO,
    /** Un envio fallido con siFalla = FINALIZAR lo dio por perdido. */
    FALLIDO,
    /** Un gateway se quedo sin camino: se corrigen las variables y se reintenta. */
    ERROR;

    /** Un caso cerrado no avanza, no recibe mensajes y no se cancela dos veces. */
    public boolean estaCerrado() {
        return this != ABIERTO && this != ERROR;
    }
}
