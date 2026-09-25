package com.facimus.procesos.ejecucion.model;

/** A donde fue a parar un mensaje que llego: los cuatro finales posibles de la correlacion. */
public enum ResultadoCorrelacion {

    /** Habia un caso abierto con esa clave y alguien esperandolo: el caso siguio. */
    ENTREGADO_A_CASO,
    /** No habia caso y el mensaje es de los que abren uno. */
    CASO_NUEVO,
    /** Hay caso, pero todavia no ha llegado a esperarlo: se queda en la bandeja y se reintenta al avanzar. */
    EN_ESPERA,
    /** El socio lo dejo dicho para mas adelante: no se mira hasta que el reloj llegue a su tick. */
    PROGRAMADO,
    /** No corresponde a ningun caso y no abre ninguno; queda escrito para que se pueda mirar. */
    DESCARTADO;

    /** Los dos que todavia pueden cambiar de resultado: el que espera a alguien y el que espera su momento. */
    public boolean puedeReintentarse() {
        return this == EN_ESPERA || this == PROGRAMADO;
    }
}
