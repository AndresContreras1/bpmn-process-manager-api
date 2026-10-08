package com.facimus.procesos.gestion.service;

/**
 * NIST SP 800-63B-4 (AAL2): cuanto puede pasar una sesion sin renovarse y cuanto puede durar en total, como los pide
 * una tienda al editar su configuracion. Un campo nulo deja el que tenia.
 */
public record LimitesDeSesion(Integer inactividadMinutos, Integer duracionHoras) {

    /** Los que no tocan las sesiones: la simulacion, que edita la misma fila. */
    public static final LimitesDeSesion SIN_CAMBIOS = new LimitesDeSesion(null, null);
}
