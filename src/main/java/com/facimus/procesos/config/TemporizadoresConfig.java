package com.facimus.procesos.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import com.facimus.procesos.common.security.ContextoDeTienda;
import com.facimus.procesos.ejecucion.service.TemporizadorService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

/**
 * PR 68 (D35): el planificador de los temporizadores de produccion. Cada tantos segundos dispara los que ya vencieron
 * con el reloj de pared, de todas las tiendas. Los de prueba no pasan por aqui: los mueve el tick de cada tienda.
 *
 * <p>Con varias instancias, la pasada la da una sola (D34). El candado dura lo que la pausa entre pasadas, y aun asi
 * cada temporizador se toma con {@code FOR UPDATE SKIP LOCKED}: si una pasada se alargara mas que su candado, la de la
 * otra instancia no dispararia dos veces el mismo. Lo que vencio con la API apagada sale en la primera pasada.
 *
 * <p>Fuera del perfil {@code test}, como cada cosa que corre sola: ahi el reloj es de la prueba, que dispara llamando
 * al service.
 */
@Slf4j
@Configuration
@EnableScheduling
@Profile("!test")
@RequiredArgsConstructor
public class TemporizadoresConfig {

    private final TemporizadorService temporizadorService;

    @Scheduled(fixedDelayString = "${temporizadores.repaso}")
    @SchedulerLock(name = "temporizadores-vencidos", lockAtLeastFor = "${temporizadores.repaso}")
    public void disparar() {
        // PR 51: se buscan entre todas las tiendas; cada uno salta con el alcance de la suya.
        int disparados = ContextoDeTienda.comoSistema(temporizadorService::dispararVencidos);
        if (disparados > 0) {
            log.info("Temporizadores: {} saltaron.", disparados);
        }
    }
}
