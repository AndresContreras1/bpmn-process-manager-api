package com.facimus.procesos.gestion.model;

/** PR 68: de donde sale un festivo del calendario de una tienda. */
public enum OrigenFestivo {
    /** De la ley de su pais. */
    CALCULADO,
    /** La tienda lo agrego o lo renombro. */
    EDITADO
}
