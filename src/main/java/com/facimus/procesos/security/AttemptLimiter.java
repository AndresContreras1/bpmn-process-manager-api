package com.facimus.procesos.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Cuenta intentos por clave en una ventana deslizante. Cuando una clave junta el maximo dentro de la ventana, espera
 * a que el intento mas viejo salga de ella. Recuerda un numero acotado de claves y olvida primero las que menos se
 * usan, asi que inventar correos no llena la memoria. Vive en esta instancia; con varias, iria a un almacen
 * compartido como Redis.
 */
public class AttemptLimiter {

    private final int maximo;
    private final Duration ventana;
    private final Clock reloj;
    private final Map<String, Deque<Instant>> intentos;

    public AttemptLimiter(int maximo, Duration ventana, int clavesMaximas, Clock reloj) {
        this.maximo = maximo;
        this.ventana = ventana;
        this.reloj = reloj;
        this.intentos = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Deque<Instant>> menosUsada) {
                return size() > clavesMaximas;
            }
        };
    }

    /** Cuanto le falta a la clave para volver a intentar, si ya gasto sus intentos dentro de la ventana. */
    public synchronized Optional<Duration> espera(String clave) {
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

    public synchronized void registrar(String clave) {
        Instant ahora = reloj.instant();
        Deque<Instant> registro = intentos.computeIfAbsent(clave, nueva -> new ArrayDeque<>());
        descartarVencidos(registro, ahora);
        registro.addLast(ahora);
    }

    public synchronized void reiniciar(String clave) {
        intentos.remove(clave);
    }

    private void descartarVencidos(Deque<Instant> registro, Instant ahora) {
        while (!registro.isEmpty() && !registro.peekFirst().plus(ventana).isAfter(ahora)) {
            registro.pollFirst();
        }
    }
}
