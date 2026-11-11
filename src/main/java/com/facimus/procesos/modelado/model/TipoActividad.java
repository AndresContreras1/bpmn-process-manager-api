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
    RECEPCION,
    /**
     * PR 55: una tarea sin decir quien la hace, como la escribe BPMN cuando solo se describe. Se publica en un proceso
     * documental; en uno ejecutable el diagnostico pide decidir su tipo.
     */
    SIN_TIPO,
    /** PR 56: un subproceso expandido: contiene su propio flujo, y los flujos no cruzan su borde. */
    SUBPROCESO,
    /** PR 56: un subproceso del que no se dibuja el contenido. */
    SUBPROCESO_COLAPSADO,
    /** PR 56: una actividad de llamada: corre otro proceso de la tienda. */
    LLAMADA;

    /** Si es un subproceso, expandido o no: en un proceso ejecutable el motor no los corre. */
    public boolean esSubproceso() {
        return this == SUBPROCESO || this == SUBPROCESO_COLAPSADO;
    }

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
