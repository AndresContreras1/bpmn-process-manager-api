package com.facimus.procesos.config;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import com.facimus.procesos.common.trabajos.ColaDeTrabajos;
import com.facimus.procesos.common.trabajos.ReglasDeLaCola;

/**
 * Los trabajadores de la cola en esta instancia: cada uno, en su hilo virtual, toma un trabajo, lo corre y vuelve por
 * otro, y si no encuentra ninguno espera un momento. Las demas instancias hacen lo mismo sobre la misma tabla, y la
 * cola reparte sin que se pisen. De paso, cada tanto devuelven a la cola lo que otra instancia dejo a medias.
 *
 * <p>Como todo lo que corre por su cuenta, vive en config y no corre en el perfil test; la prueba de los trabajadores
 * lo enciende.
 */
@Component
@ConditionalOnProperty(name = "trabajos.trabajar", havingValue = "true", matchIfMissing = true)
public class TrabajadoresDeLaCola implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(TrabajadoresDeLaCola.class);
    private static final Duration REPASO_DE_ATASCADOS = Duration.ofMinutes(1);

    private final ColaDeTrabajos cola;
    private final ReglasDeLaCola reglas;
    private final List<Thread> hilos = new ArrayList<>();
    private final AtomicReference<Instant> ultimoRepaso = new AtomicReference<>(Instant.EPOCH);
    private volatile boolean corriendo;

    public TrabajadoresDeLaCola(ColaDeTrabajos cola, ReglasDeLaCola reglas) {
        this.cola = cola;
        this.reglas = reglas;
    }

    @Override
    public void start() {
        corriendo = true;
        for (int numero = 1; numero <= reglas.trabajadores(); numero++) {
            hilos.add(Thread.ofVirtual().name("trabajador-" + numero).start(this::trabajar));
        }
    }

    /** Cada trabajador termina lo que tiene entre manos y no toma otro. */
    @Override
    public void stop() {
        corriendo = false;
        for (Thread hilo : hilos) {
            try {
                hilo.join(reglas.pausa().plusSeconds(30));
            } catch (InterruptedException interrumpido) {
                Thread.currentThread().interrupt();
            }
        }
        hilos.clear();
    }

    @Override
    public boolean isRunning() {
        return corriendo;
    }

    private void trabajar() {
        while (corriendo) {
            try {
                if (!cola.procesarUno()) {
                    repasarAtascados();
                    pausar();
                }
            } catch (RuntimeException fallo) {
                log.warn("Un trabajador de la cola no pudo tomar trabajo; vuelve a intentar en {}", reglas.pausa(),
                        fallo);
                pausar();
            }
        }
    }

    private void repasarAtascados() {
        Instant ahora = Instant.now();
        Instant anterior = ultimoRepaso.get();
        if (anterior.plus(REPASO_DE_ATASCADOS).isBefore(ahora) && ultimoRepaso.compareAndSet(anterior, ahora)) {
            cola.reabrirAtascados();
        }
    }

    private void pausar() {
        try {
            Thread.sleep(reglas.pausa());
        } catch (InterruptedException interrumpido) {
            Thread.currentThread().interrupt();
            corriendo = false;
        }
    }
}
