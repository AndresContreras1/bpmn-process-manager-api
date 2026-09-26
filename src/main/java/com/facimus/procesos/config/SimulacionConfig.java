package com.facimus.procesos.config;

import java.util.List;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import com.facimus.procesos.ejecucion.service.SimulacionService;
import com.facimus.procesos.gestion.service.ConfiguracionTiendaService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * D8: el reloj de las tiendas que pidieron que corriera solo. Cada tantos segundos avanza un tick el reloj de cada
 * tienda en modo AUTOMATICO, que es lo que hace falta para dejar la simulacion andando en una demo.
 *
 * <p>Queda fuera del perfil {@code test} a proposito, por lo mismo que la purga: una prueba no puede tener algo
 * por detras moviendole el reloj a media prueba. Ahi el tick se da llamando al service, que es lo que se ejerce.
 * <p>
 * {@code SIMULACION_TICK=-} no sirve para apagarlo: lo que lo apaga es que ninguna tienda este en AUTOMATICO, que
 * es la forma de decirlo que ya existe y que cada tienda decide por su cuenta.
 */
@Slf4j
@Configuration
@EnableScheduling
@Profile("!test")
@RequiredArgsConstructor
public class SimulacionConfig {

    private final ConfiguracionTiendaService configuracionTiendaService;
    private final SimulacionService simulacionService;

    @Scheduled(fixedDelayString = "${simulacion.tick}")
    public void mover() {
        List<Long> tiendas = configuracionTiendaService.tiendasEnAutomatico();
        for (Long tienda : tiendas) {
            simulacionService.tick(tienda, 1);
        }
        if (!tiendas.isEmpty()) {
            log.info("Simulacion: un tick en {} tiendas.", tiendas.size());
        }
    }
}
