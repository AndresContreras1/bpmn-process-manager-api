package com.facimus.procesos.modelado.model;

/** PR 40: por que se guardo un punto del borrador. */
public enum MotivoDeRevision {
    /** Pasaron los minutos que tocaban mientras se editaba. */
    AUTOGUARDADO,
    /** Quien editaba cerro el editor. */
    CIERRE,
    /** Lo que habia justo antes de restaurar otra revision: restaurar tambien se puede deshacer. */
    ANTES_DE_RESTAURAR,
    /** PR 64: se pidio una revision para publicarlo: este es el borrador que se revisa. */
    SOLICITUD
}
