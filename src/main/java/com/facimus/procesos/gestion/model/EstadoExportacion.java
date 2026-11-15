package com.facimus.procesos.gestion.model;

/** PR 66: una exportacion se pide, la cola la arma, se baja mientras vale su enlace y despues se borra. */
public enum EstadoExportacion {

    /** Pedida: la cola la esta armando. Hay una sola asi por tienda. */
    PENDIENTE,

    /** En el almacen, con su huella; el enlace del correo la baja hasta que vence. */
    LISTA,

    /** No se pudo armar, con el motivo. Se puede pedir otra. */
    FALLIDA,

    /** Vencio su enlace y el ZIP se borro del almacen; la fila queda como constancia. */
    VENCIDA
}
