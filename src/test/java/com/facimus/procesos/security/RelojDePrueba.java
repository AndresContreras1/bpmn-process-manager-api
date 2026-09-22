package com.facimus.procesos.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Un reloj que solo avanza cuando el test lo pide. */
final class RelojDePrueba extends Clock {

    private Instant ahora = Instant.parse("2026-09-22T12:00:00Z");

    void avanzar(Duration tiempo) {
        ahora = ahora.plus(tiempo);
    }

    @Override
    public Instant instant() {
        return ahora;
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        throw new UnsupportedOperationException("El reloj de prueba siempre va en UTC.");
    }
}
