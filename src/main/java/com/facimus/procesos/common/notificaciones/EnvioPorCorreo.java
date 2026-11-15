package com.facimus.procesos.common.notificaciones;

/**
 * PR 63: si una notificacion tambien llega por correo, y cuando. Cada persona lo elige por tipo; en la aplicacion y en
 * el flujo en vivo llegan siempre.
 */
public enum EnvioPorCorreo {
    /** Un correo por cada una, en cuanto pasa. */
    INMEDIATO,
    /** Un correo al dia con las que sigan sin leer. */
    RESUMEN_DIARIO,
    /** Ninguno. */
    NUNCA
}
