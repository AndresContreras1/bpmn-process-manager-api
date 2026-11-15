package com.facimus.procesos.modelado.model;

/** PR 64: en que quedo una solicitud de revision. Solo la abierta recibe decisiones. */
public enum EstadoSolicitud {
    /** Espera decisiones de sus aprobadores. */
    ABIERTA,
    /** Tuvo todas sus aprobaciones y se publico lo congelado. */
    PUBLICADA,
    /** Tuvo todas sus aprobaciones, pero lo congelado ya no se podia publicar: el motivo dice por que. */
    APROBADA_SIN_PUBLICAR,
    /** Un aprobador pidio cambios. */
    CAMBIOS_PEDIDOS,
    /** Un aprobador la rechazo. */
    RECHAZADA,
    /** Quien la pidio la retiro. */
    RETIRADA
}
