package com.facimus.procesos.ejecucion.service.impl;

import org.springframework.stereotype.Component;

import com.facimus.procesos.ejecucion.puerto.ParametrosDeSimulacion;
import com.facimus.procesos.gestion.dto.response.ParametrosSimulacionResponse;
import com.facimus.procesos.gestion.service.ConfiguracionTiendaService;

import lombok.RequiredArgsConstructor;

/**
 * Lo que la tienda decidio sobre sus socios, traducido a lo que un socio necesita. Son dos formas de lo mismo a
 * proposito: una es lo que la tienda guarda y responde por su API, y la otra es lo que viaja con un mensaje. Si
 * fueran la misma, quien implemente un socio tendria que conocer la gestion de tiendas, y D1 dice que no.
 */
@Component
@RequiredArgsConstructor
class ParametrosDeLaTienda {

    private final ConfiguracionTiendaService configuracionTiendaService;

    ParametrosDeSimulacion de(Long empresaId) {
        ParametrosSimulacionResponse suyos = configuracionTiendaService.obtener(empresaId).simulacion();
        return new ParametrosDeSimulacion(suyos.semilla(), suyos.tasaRechazoPagos(), suyos.ticksRespuestaPagos(),
                suyos.reglaRechazoPagos(), suyos.ticksRespuestaTransporte(), suyos.ticksEntrega(),
                suyos.tasaPerdidaEnvios(), suyos.tasaFalloNotificaciones());
    }
}
