package com.facimus.procesos.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import com.facimus.procesos.gestion.event.SesionesCerradas;
import com.facimus.procesos.gestion.service.SesionService;

/**
 * Las sesiones cerradas cuyos access tokens todavia no vencen. El filtro JWT las mira aqui, en memoria y sin ir a la
 * base: un token de una sesion cerrada deja de servir al instante y no cuando vence. Cada sesion se olvida cuando ya
 * vencio el ultimo token que pudo emitir. Vive en esta instancia; con varias, iria a un almacen compartido como Redis.
 */
@Component
public class RevokedSessions {

    private final Map<String, Instant> revocadas = new ConcurrentHashMap<>();
    private final SesionService sesionService;
    private final Duration vigenciaToken;
    private final Clock reloj;

    public RevokedSessions(SesionService sesionService, @Value("${jwt.expiration-seconds}") long vigenciaTokenSegundos,
            Clock reloj) {
        this.sesionService = sesionService;
        this.vigenciaToken = Duration.ofSeconds(vigenciaTokenSegundos);
        this.reloj = reloj;
    }

    public boolean estaRevocada(String sesion) {
        Instant hasta = revocadas.get(sesion);
        return hasta != null && hasta.isAfter(reloj.instant());
    }

    /** Despues del commit: si la transaccion que cerraba las sesiones falla, las sesiones siguen abiertas. */
    @TransactionalEventListener(fallbackExecution = true)
    public void alCerrarse(SesionesCerradas evento) {
        revocar(evento.codigos());
    }

    /** Un reinicio vacia la memoria: se recuperan de la base las sesiones cerradas cuyos tokens todavia no vencen. */
    @EventListener(ApplicationReadyEvent.class)
    public void recuperarCerradas() {
        revocar(sesionService.cerradasEnLosUltimos(vigenciaToken));
    }

    private void revocar(List<String> codigos) {
        Instant ahora = reloj.instant();
        revocadas.values().removeIf(hasta -> !hasta.isAfter(ahora));
        codigos.forEach(codigo -> revocadas.put(codigo, ahora.plus(vigenciaToken)));
    }
}
