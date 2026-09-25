package com.facimus.procesos.gestion.model;

import com.facimus.procesos.common.model.RolAcceso;

/** D16: quien puede crear y editar la estructura de los diagramas, es decir los pools y las lanes. */
public enum PoliticaEstructura {

    /** Solo los administradores. Los editores siguen modelando lo de dentro: nodos, arcos y mensajes. */
    SOLO_ADMINISTRADOR,

    /** Administradores y editores, que es como funcionaba la plataforma antes de que esto se pudiera elegir. */
    ADMINISTRADOR_Y_EDITOR;

    /** Eliminar pools y lanes sigue siendo del administrador, elija lo que elija la tienda. */
    public boolean permiteA(RolAcceso rol) {
        return rol == RolAcceso.ADMINISTRADOR || this == ADMINISTRADOR_Y_EDITOR && rol == RolAcceso.EDITOR;
    }
}
