package com.facimus.procesos.modelado.model;

/** Quien hace el trabajo de una actividad, y si ese trabajo consiste en hablar con otro participante. */
public enum TipoActividad {

    /** La hace una persona del rol de su lane. */
    USUARIO,
    /** La hace la tienda sin intervencion de nadie. */
    SERVICIO,
    /** Manda un mensaje a otro participante. */
    ENVIO,
    /** Espera un mensaje de otro participante. */
    RECEPCION;

    /** Envio y recepcion existen para intercambiar un mensaje: sin mensaje no tienen sentido. */
    public boolean intercambiaMensajes() {
        return this == ENVIO || this == RECEPCION;
    }

    /** Una actividad de envio manda el mensaje; una de servicio puede mandarlo como parte de su tarea. */
    public boolean puedeEnviar() {
        return this == ENVIO || this == SERVICIO;
    }

    /** Solo una actividad de recepcion se queda esperando un mensaje. */
    public boolean puedeRecibir() {
        return this == RECEPCION;
    }
}
