package com.facimus.procesos.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import com.facimus.procesos.common.security.ContextoDeTienda;
import com.facimus.procesos.ejecucion.service.PlazoService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

/**
 * PR 71: el repaso de los plazos de produccion. Cada tanto mueve las tareas y los casos que entraron en su ultimo cuarto
 * o pasaron su plazo con el reloj de pared, de todas las tiendas, y avisa. Los de prueba no pasan por aqui: los mueve
 * el tick de cada tienda.
 *
 * <p>Con varias instancias, la pasada la da una sola (D34). Aun asi cada plazo se mueve con una escritura que solo pasa
 * si sigue como estaba: si una pasada se alargara mas que su candado, la de la otra instancia no avisaria dos veces.
 * Lo que cambio con la API apagada se mueve, y se avisa, en la primera pasada.
 *
 * <p>Fuera del perfil {@code test}, como cada cosa que corre sola: ahi el reloj es de la prueba, que repasa llamando al
 * service.
 */
@Slf4j
@Configuration
@EnableScheduling
@Profile("!test")
@RequiredArgsConstructor
public class PlazosConfig {

    private final PlazoService plazoService;

    @Scheduled(fixedDelayString = "${plazos.repaso}")
    @SchedulerLock(name = "plazos", lockAtLeastFor = "${plazos.repaso}")
    public void repasar() {
        // PR 51: se buscan entre todas las tiendas; cada uno se mueve con el alcance de la suya.
        int cambiaron = ContextoDeTienda.comoSistema(plazoService::revisarVencidos);
        if (cambiaron > 0) {
            log.info("Plazos: {} cambiaron de estado.", cambiaron);
        }
    }
}
