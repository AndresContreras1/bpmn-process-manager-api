package com.facimus.procesos.modelado.mapper;

import com.facimus.procesos.modelado.dto.response.PlazoResponse;
import com.facimus.procesos.modelado.model.NodoFlujo;

/** PR 71: el plazo de un nodo como lo responde la API, el mismo para una actividad y para un evento. */
final class PlazosDelModelo {

    private PlazosDelModelo() {
    }

    /** El plazo, o nulo si el nodo no tiene: el check de la base no deja una cantidad sin unidad ni al reves. */
    static PlazoResponse plazo(NodoFlujo nodo) {
        return nodo.getPlazoUnidad() == null || nodo.getPlazoCantidad() == null ? null
                : new PlazoResponse(nodo.getPlazoCantidad(), nodo.getPlazoUnidad());
    }
}
