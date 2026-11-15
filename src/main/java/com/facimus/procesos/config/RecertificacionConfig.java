package com.facimus.procesos.config;

import java.time.Clock;
import java.time.LocalDate;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import com.facimus.procesos.common.security.ContextoDeTienda;
import com.facimus.procesos.gestion.service.RecertificacionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

/**
 * PR 64: una vez al dia, los avisos de recertificacion: un trabajo en la cola por tienda con algun proceso cuya
 * revision llego o vencio. Una sola instancia lo hace (ShedLock), y cruza tiendas a proposito, como el resumen de las
 * notificaciones. El dia es el del reloj de la API, en UTC ({@code RECERTIFICACION_CRON}); {@code -} lo apaga.
 */
@Slf4j
@Configuration
@EnableScheduling
@Profile("!test")
@RequiredArgsConstructor
public class RecertificacionConfig {

    private final RecertificacionService recertificacion;
    private final Clock reloj;

    @Scheduled(cron = "${recertificacion.cron:0 30 6 * * *}", zone = "UTC")
    @SchedulerLock(name = "avisos-de-recertificacion", lockAtLeastFor = "PT5M", lockAtMostFor = "PT30M")
    public void avisar() {
        LocalDate hoy = LocalDate.now(reloj);
        int tiendas = ContextoDeTienda.comoSistema(() -> recertificacion.encolarAvisos(hoy));
        if (tiendas > 0) {
            log.info("Recertificacion: {} tiendas con avisos el {}.", tiendas, hoy);
        }
    }
}
