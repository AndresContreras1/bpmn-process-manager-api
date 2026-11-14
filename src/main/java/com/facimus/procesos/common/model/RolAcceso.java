package com.facimus.procesos.common.model;

public enum RolAcceso {
    ADMINISTRADOR,
    EDITOR,
    SOLO_LECTURA;

    /** PR 70: quien puede tener y completar una tarea: la matriz de rutas no le deja completar a quien solo lee. */
    public boolean completaTareas() {
        return this != SOLO_LECTURA;
    }
}
