package com.facimus.procesos.config;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import com.facimus.procesos.common.security.ContextoDeTienda;
import com.facimus.procesos.gestion.service.AnclaDeBitacoraService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

/**
 * PR 61: una vez al dia, el ancla de la bitacora de cada tienda que cambio. Una sola instancia lo hace (ShedLock), y
 * cruza tiendas a proposito, como la purga. El dia es el de UTC, el mismo para todas las instancias.
 */
@Slf4j
@Configuration
@EnableScheduling
@Profile("!test")
@RequiredArgsConstructor
public class AnclaDeBitacoraConfig {

    private final AnclaDeBitacoraService anclas;
    private final Clock reloj;

    @Scheduled(cron = "${bitacora.ancla.cron:0 15 0 * * *}", zone = "UTC")
    @SchedulerLock(name = "ancla-de-bitacora", lockAtLeastFor = "PT5M", lockAtMostFor = "PT1H")
    public void anclar() {
        LocalDate hoy = LocalDate.now(reloj.withZone(ZoneOffset.UTC));
        int ancladas = ContextoDeTienda.comoSistema(() -> anclas.anclar(hoy));
        if (ancladas > 0) {
            log.info("Ancla de la bitacora: {} tiendas ancladas el {}.", ancladas, hoy);
        }
    }
}
