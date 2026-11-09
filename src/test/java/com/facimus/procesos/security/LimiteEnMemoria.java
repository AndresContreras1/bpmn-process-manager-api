package com.facimus.procesos.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * El limite de intentos en memoria, para las pruebas unitarias de quien lo usa: la misma ventana deslizante que
 * {@link IntentosDeLogin} guarda en la base, sin la base.
 */
final class LimiteEnMemoria implements LimiteDeIntentos {

    private final int maximo;
    private final Duration ventana;
    private final Clock reloj;
    private final Map<String, Deque<Instant>> intentos = new HashMap<>();

    LimiteEnMemoria(int maximo, Duration ventana, Clock reloj) {
        this.maximo = maximo;
        this.ventana = ventana;
        this.reloj = reloj;
    }

    @Override
    public Optional<Duration> espera(String clave) {
        Deque<Instant> registro = intentos.get(clave);
        if (registro == null) {
            return Optional.empty();
        }
        Instant ahora = reloj.instant();
        descartarVencidos(registro, ahora);
        if (registro.size() < maximo) {
            return Optional.empty();
        }
        return Optional.of(Duration.between(ahora, registro.peekFirst().plus(ventana)));
    }

    @Override
    public void registrar(String clave) {
        Instant ahora = reloj.instant();
        Deque<Instant> registro = intentos.computeIfAbsent(clave, nueva -> new ArrayDeque<>());
        descartarVencidos(registro, ahora);
        registro.addLast(ahora);
    }

    @Override
    public void reiniciar(String clave) {
        intentos.remove(clave);
    }

    private void descartarVencidos(Deque<Instant> registro, Instant ahora) {
        while (!registro.isEmpty() && !registro.peekFirst().plus(ventana).isAfter(ahora)) {
            registro.pollFirst();
        }
    }
}
