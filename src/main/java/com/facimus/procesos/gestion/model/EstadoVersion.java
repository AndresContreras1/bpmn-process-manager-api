package com.facimus.procesos.gestion.model;

/** En que situacion esta una version publicada. Una version nunca se borra: se retira. */
public enum EstadoVersion {

    /** La que se lee y, cuando exista la ejecucion, la que abre casos nuevos. */
    VIGENTE,

    /** Se deja de usar. Sigue ahi porque los casos que se abrieron con ella se leen contra ella. */
    RETIRADA
}
