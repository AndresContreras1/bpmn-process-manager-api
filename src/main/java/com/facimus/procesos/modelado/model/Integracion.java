package com.facimus.procesos.modelado.model;

/** Con que clase de socio habla un pool de caja negra. Hoy solo describe; los socios llegan simulados. */
public enum Integracion {

    /** Ninguna: el pool es la tienda o un participante sin sistema detras. */
    NINGUNA,
    CLIENTE,
    PAGOS,
    TRANSPORTE,
    NOTIFICACIONES
}
