package com.facimus.procesos.modelado.model;

/** Por donde sale un mensaje hacia otro participante. Describe el medio, no lo ejecuta. */
public enum TipoDestino {

    /** Un correo a una persona. */
    CORREO,
    /** Una llamada a un servicio del otro participante. */
    SERVICIO_WEB,
    /** Un mensaje que se deja en una cola y el otro participante recoge cuando puede. */
    COLA
}
