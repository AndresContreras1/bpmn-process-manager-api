package com.facimus.procesos.gestion.service;

import java.util.Optional;

/**
 * PR 60: de que proceso es un elemento. Las concesiones son de gestion y los elementos viven en modelado: gestion
 * declara lo que necesita saber y modelado lo implementa, asi la dependencia va en un solo sentido.
 */
public interface ProcesoDeElemento {

    /** El tipo es el primer tramo de su ruta: pools, lanes, actividades, gateways, eventos, nodos, arcos, ... */
    Optional<Long> procesoDe(Long empresaId, String tipo, Long id);
}
