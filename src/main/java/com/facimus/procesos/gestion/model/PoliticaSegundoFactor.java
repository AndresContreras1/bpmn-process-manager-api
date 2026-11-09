package com.facimus.procesos.gestion.model;

import com.facimus.procesos.common.model.RolAcceso;

/** PR 49: a quien le exige la tienda un segundo factor para trabajar. */
public enum PoliticaSegundoFactor {

    /** Cada persona decide si lo activa: como era antes de que esto se pudiera elegir. */
    OPCIONAL,

    /** Los administradores, que pueden dar de baja la tienda y gestionar a todos, no trabajan sin el. */
    ADMINISTRADORES,

    /** Nadie trabaja sin el. */
    TODOS;

    public boolean exigeA(RolAcceso rol) {
        return this == TODOS || this == ADMINISTRADORES && rol == RolAcceso.ADMINISTRADOR;
    }
}
