package com.facimus.procesos.ejecucion.service;

import com.facimus.procesos.ejecucion.dto.response.PanelDeSimulacionResponse;

/** D8: el reloj de la tienda y lo que pasa cuando se mueve. */
public interface SimulacionService {

    /** Donde esta la simulacion de una tienda: su reloj, quien lo mueve y que queda en las bandejas. */
    PanelDeSimulacionResponse panel(Long empresaId);

    /**
     * Adelanta el reloj y entrega lo que con ese tick ya tendria que haber llegado. Cada mensaje se entrega en su
     * propia transaccion, con su caso bloqueado (D3): un tick que mueve veinte pedidos no los mueve a la vez.
     */
    PanelDeSimulacionResponse tick(Long empresaId, int ticks);
}
