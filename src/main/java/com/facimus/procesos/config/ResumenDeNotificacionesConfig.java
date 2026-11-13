package com.facimus.procesos.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import com.facimus.procesos.common.security.ContextoDeTienda;
import com.facimus.procesos.gestion.service.NotificacionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

/**
 * PR 63: una vez al dia, el resumen de las notificaciones de quien lo eligio: un correo en la cola por persona con
 * alguna que lo espera. Una sola instancia lo hace (ShedLock), y cruza tiendas a proposito, como la purga. La hora es
 * la misma para todos, en UTC ({@code NOTIFICACIONES_RESUMEN_CRON}); {@code -} lo apaga.
 */
@Slf4j
@Configuration
@EnableScheduling
@Profile("!test")
@RequiredArgsConstructor
public class ResumenDeNotificacionesConfig {

    private final NotificacionService notificaciones;

    @Scheduled(cron = "${notificaciones.resumen.cron:0 0 7 * * *}", zone = "UTC")
    @SchedulerLock(name = "resumen-de-notificaciones", lockAtLeastFor = "PT5M", lockAtMostFor = "PT30M")
    public void resumir() {
        int personas = ContextoDeTienda.comoSistema(notificaciones::encolarResumenes);
        if (personas > 0) {
            log.info("Resumen de notificaciones: {} correos en la cola.", personas);
        }
    }
}
