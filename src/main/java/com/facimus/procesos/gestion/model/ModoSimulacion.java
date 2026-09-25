package com.facimus.procesos.gestion.model;

/** D8: quien mueve el reloj de la tienda. */
public enum ModoSimulacion {

    /** Lo mueve quien prueba, un tick cada vez: una prueba sabe exactamente en que momento esta. */
    MANUAL,
    /** Lo mueve un trabajo cada tantos segundos, para mostrar la simulacion corriendo sola. */
    AUTOMATICO;

    /** Solo el automatico necesita que algo lo empuje por detras. */
    public boolean necesitaTrabajo() {
        return this == AUTOMATICO;
    }
}
