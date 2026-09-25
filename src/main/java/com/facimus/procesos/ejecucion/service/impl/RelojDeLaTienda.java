package com.facimus.procesos.ejecucion.service.impl;

import org.springframework.stereotype.Component;

import com.facimus.procesos.gestion.service.ConfiguracionTiendaService;

import lombok.RequiredArgsConstructor;

/**
 * D8: el tiempo de la simulacion. Un caso no se fecha con la hora del servidor sino con el reloj de su tienda, un
 * contador de ticks que mueve quien prueba; asi una prueba dice "a los tres ticks llego la respuesta" y eso
 * significa siempre lo mismo, corra en el portatil de quien sea o en el CI.
 *
 * <p>La fecha de pared sigue guardandose al lado, porque quien mira la bitacora quiere las dos cosas: el momento de
 * la simulacion y el dia en que se ejecuto.
 */
@Component
@RequiredArgsConstructor
class RelojDeLaTienda {

    private final ConfiguracionTiendaService configuracionTiendaService;

    /** En que tick va la tienda. Se lee una vez por operacion y viaja con ella dentro de un {@link Momento}. */
    int ahora(Long empresaId) {
        return configuracionTiendaService.reloj(empresaId);
    }
}
