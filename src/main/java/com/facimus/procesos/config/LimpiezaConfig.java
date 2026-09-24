package com.facimus.procesos.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import com.facimus.procesos.gestion.service.Limpieza;
import com.facimus.procesos.gestion.service.LimpiezaService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * El reloj de la purga (D20). Queda fuera del perfil {@code test} a proposito: una prueba no puede tener un trabajo
 * corriendo por detras que le borre filas a media prueba, y la purga se ejerce llamando al service.
 * <p>
 * {@code LIMPIEZA_CRON=-} la apaga sin recompilar, que es lo que querria quien prefiera barrer desde fuera.
 */
@Slf4j
@Configuration
@EnableScheduling
@Profile("!test")
@RequiredArgsConstructor
public class LimpiezaConfig {

    private final LimpiezaService limpiezaService;

    @Scheduled(cron = "${limpieza.cron}")
    public void limpiar() {
        Limpieza limpieza = limpiezaService.limpiar();
        if (limpieza.total() > 0) {
            log.info("Limpieza: {} refresh tokens, {} sesiones y {} claves de idempotencia.",
                    limpieza.refreshTokens(), limpieza.sesiones(), limpieza.clavesIdempotencia());
        }
    }
}
