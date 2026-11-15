package com.facimus.procesos.ejecucion.service;

/**
 * PR 71: el repaso de los plazos. El motor los arma y los cierra; aqui se mueven mientras esperan: a tiempo, por
 * vencer y vencida, con un aviso por cada cambio, una sola vez.
 */
public interface PlazoService {

    /**
     * Mueve los plazos de produccion que ya cambiaron con el reloj de pared, de todas las tiendas, cada uno en su
     * transaccion. Lo llama el planificador como sistema. Devuelve cuantos cambiaron.
     */
    int revisarVencidos();

    /** Mueve los plazos de prueba de una tienda que cambian en ese tick o antes. Lo llama el tick de la tienda. */
    int revisarEnTick(Long empresaId, int tick);
}
