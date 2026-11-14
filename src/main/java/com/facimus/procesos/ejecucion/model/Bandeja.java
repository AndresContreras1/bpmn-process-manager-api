package com.facimus.procesos.ejecucion.model;

/** PR 70: que tareas se piden: las de la tienda, las de quien pregunta, las de sus roles o las que nadie tiene. */
public enum Bandeja {
    /** Las de la tienda entera. */
    TODAS,
    /** Las que tiene quien pregunta: las unicas que puede completar sin tomarlas antes. */
    MIAS,
    /** Las de los roles de proceso a los que pertenece quien pregunta, las tenga quien las tenga (D13). */
    DE_MIS_ROLES,
    /** Las que no tiene nadie: esperan en la bandeja de su rol a que alguien las tome. */
    SIN_ASIGNAR
}
