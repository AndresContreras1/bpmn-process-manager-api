package com.facimus.procesos.modelado.service.impl;

import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.modelado.model.NodoFlujo;

/**
 * R-31 y R-32: por donde empieza y por donde termina un proceso. La regla se aplica en dos momentos, al crear o
 * mover un arco y al cambiar el tipo de un evento que ya tiene arcos, asi que el mensaje vive en un solo sitio.
 */
final class ReglasDeEventos {

    static final String SIN_ENTRANTES = "Un evento de inicio no puede tener arcos entrantes.";
    static final String SIN_SALIENTES = "Un evento de fin no puede tener arcos salientes.";

    private ReglasDeEventos() {
    }

    /** Los extremos de un arco: de un evento de fin no sale nada, y a un evento de inicio no llega nada. */
    static void exigirNodosConectables(NodoFlujo origen, NodoFlujo destino) {
        if (!origen.aceptaArcosSalientes()) {
            throw new ReglaNegocioException(SIN_SALIENTES);
        }
        if (!destino.aceptaArcosEntrantes()) {
            throw new ReglaNegocioException(SIN_ENTRANTES);
        }
    }
}
