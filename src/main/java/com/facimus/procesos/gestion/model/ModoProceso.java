package com.facimus.procesos.gestion.model;

/**
 * PR 55: para que es un proceso. Uno documental se modela, se publica y se documenta, pero no abre casos, y su
 * diagnostico solo mira la estructura; uno ejecutable es lo que el producto corre.
 */
public enum ModoProceso {
    DOCUMENTAL,
    EJECUTABLE;

    public boolean ejecuta() {
        return this == EJECUTABLE;
    }
}
