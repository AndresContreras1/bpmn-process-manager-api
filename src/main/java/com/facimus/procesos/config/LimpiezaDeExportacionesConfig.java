package com.facimus.procesos.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import com.facimus.procesos.common.security.ContextoDeTienda;
import com.facimus.procesos.gestion.service.ExportacionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

/**
 * PR 66: cada hora, las exportaciones cuyo enlace vencio se borran del almacen, y las que la cola no termino a tiempo
 * quedan fallidas para que se pueda pedir otra. Cada hora y no cada noche: un ZIP con cada cosa de una tienda no se
 * queda en el almacen mas de una hora despues de vencer. Una sola instancia lo hace (ShedLock), y cruza tiendas a
 * proposito, como la purga. {@code EXPORTACIONES_LIMPIEZA_CRON=-} la apaga.
 */
@Slf4j
@Configuration
@EnableScheduling
@Profile("!test")
@RequiredArgsConstructor
public class LimpiezaDeExportacionesConfig {

    private final ExportacionService exportaciones;

    @Scheduled(cron = "${exportaciones.limpieza.cron:0 15 * * * *}", zone = "UTC")
    @SchedulerLock(name = "limpieza-de-exportaciones", lockAtLeastFor = "PT1M", lockAtMostFor = "PT30M")
    public void limpiar() {
        int cambiadas = ContextoDeTienda.comoSistema(exportaciones::limpiar);
        if (cambiadas > 0) {
            log.info("Limpieza de exportaciones: {} vencidas o abandonadas.", cambiadas);
        }
    }
}
