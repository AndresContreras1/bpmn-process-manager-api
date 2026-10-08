package com.facimus.procesos.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import com.facimus.procesos.gestion.service.Limpieza;
import com.facimus.procesos.gestion.service.LimpiezaService;
import com.facimus.procesos.modelado.service.RevisionService;
import com.facimus.procesos.security.IntentosDeLogin;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

/**
 * El reloj de la purga (D20). Queda fuera del perfil {@code test} a proposito: una prueba no puede tener un trabajo
 * corriendo por detras que le borre filas a media prueba, y la purga se ejerce llamando al service.
 * <p>
 * {@code LIMPIEZA_CRON=-} la apaga sin recompilar, que es lo que querria quien prefiera barrer desde fuera.
 * <p>
 * Con varias instancias la corre una sola (D34): el candado se queda tomado cinco minutos aunque la purga termine
 * antes, por si el reloj de otra maquina le hace llegar a las 3:30 unos segundos despues.
 */
@Slf4j
@Configuration
@EnableScheduling
@Profile("!test")
@RequiredArgsConstructor
public class LimpiezaConfig {

    private final LimpiezaService limpiezaService;
    private final RevisionService revisionService;
    private final IntentosDeLogin intentosDeLogin;

    @Scheduled(cron = "${limpieza.cron}")
    @SchedulerLock(name = "limpieza", lockAtLeastFor = "PT5M", lockAtMostFor = "PT30M")
    public void limpiar() {
        Limpieza limpieza = limpiezaService.limpiar();
        if (limpieza.total() > 0) {
            log.info("Limpieza: {} refresh tokens, {} sesiones y {} claves de idempotencia.",
                    limpieza.refreshTokens(), limpieza.sesiones(), limpieza.clavesIdempotencia());
        }
        int intentos = intentosDeLogin.olvidarVencidos();
        if (intentos > 0) {
            log.info("Limpieza: {} intentos de login que ya salieron de su ventana.", intentos);
        }
        int revisiones = revisionService.olvidarSuperadas();
        if (revisiones > 0) {
            log.info("Limpieza: {} revisiones con IA que ya tenian una mas nueva.", revisiones);
        }
    }
}
