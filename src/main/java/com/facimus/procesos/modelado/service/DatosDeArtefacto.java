package com.facimus.procesos.modelado.service;

import com.facimus.procesos.modelado.model.TipoArtefacto;

/**
 * PR 55: lo que se dice de un artefacto al crearlo o editarlo. Va en una lane, con su posicion relativa a ella como
 * los nodos, o suelto en un pool o en el diagrama; sin tamano, toma el de su tipo.
 */
public record DatosDeArtefacto(TipoArtefacto tipo, String texto, Long poolId, Long laneId, int posicionX,
        int posicionY, Integer ancho, Integer alto) {
}
