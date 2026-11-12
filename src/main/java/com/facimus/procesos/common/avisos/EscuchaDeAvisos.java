package com.facimus.procesos.common.avisos;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.postgresql.PGConnection;
import org.postgresql.PGNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * PR 58: escucha los avisos de PostgreSQL con una conexion propia, fuera del pool, y da el latido de esta replica. No
 * es un {@code @Scheduled}: esos corren en una sola replica, y el latido es de cada una.
 *
 * <p>PR 63: una sola escucha por replica para todos los canales: el en vivo de un proceso y las notificaciones de cada
 * persona. Cada aviso va al receptor de su canal.
 */
@Component
@ConditionalOnProperty(name = "avisos.escuchar", havingValue = "true", matchIfMissing = true)
public class EscuchaDeAvisos implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(EscuchaDeAvisos.class);
    private static final int ESPERA_POR_AVISOS_MS = 500;
    private static final Duration LATIDO = Duration.ofSeconds(25);
    private static final Duration PAUSA_TRAS_UN_CORTE = Duration.ofSeconds(5);
    /** El nombre del canal va dentro de la sentencia: solo letras minusculas y guiones bajos. */
    private static final Pattern CANAL = Pattern.compile("[a-z_]{1,63}");

    private final JdbcConnectionDetails base;
    private final Map<String, ReceptorDeAvisos> receptores;
    private volatile boolean corriendo;
    private Thread hilo;

    public EscuchaDeAvisos(JdbcConnectionDetails base, List<ReceptorDeAvisos> receptores) {
        this.base = base;
        for (ReceptorDeAvisos receptor : receptores) {
            if (!CANAL.matcher(receptor.canal()).matches()) {
                throw new IllegalArgumentException("El canal " + receptor.canal() + " no es un nombre valido.");
            }
        }
        this.receptores = receptores.stream().collect(Collectors.toMap(ReceptorDeAvisos::canal, Function.identity()));
    }

    @Override
    public void start() {
        corriendo = true;
        hilo = Thread.ofVirtual().name("escucha-de-avisos").start(this::escuchar);
    }

    @Override
    public void stop() {
        corriendo = false;
        try {
            hilo.join(Duration.ofSeconds(2));
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public boolean isRunning() {
        return corriendo;
    }

    private void escuchar() {
        while (corriendo) {
            try (Connection conexion = DriverManager.getConnection(base.getJdbcUrl(), base.getUsername(),
                    base.getPassword())) {
                try (Statement sentencia = conexion.createStatement()) {
                    for (String canal : receptores.keySet()) {
                        sentencia.execute("LISTEN " + canal);
                    }
                }
                recibirAvisos(conexion.unwrap(PGConnection.class));
            } catch (SQLException | RuntimeException corte) {
                if (corriendo) {
                    log.warn("Se corto la escucha de avisos; se reabre en {}", PAUSA_TRAS_UN_CORTE, corte);
                    pausar();
                }
            }
        }
    }

    private void recibirAvisos(PGConnection postgres) throws SQLException {
        Instant proximoLatido = Instant.now().plus(LATIDO);
        while (corriendo) {
            if (!Instant.now().isBefore(proximoLatido)) {
                receptores.values().forEach(ReceptorDeAvisos::latir);
                proximoLatido = Instant.now().plus(LATIDO);
            }
            PGNotification[] avisos = postgres.getNotifications(ESPERA_POR_AVISOS_MS);
            if (avisos != null) {
                for (PGNotification aviso : avisos) {
                    ReceptorDeAvisos receptor = receptores.get(aviso.getName());
                    if (receptor != null) {
                        receptor.recibir(aviso.getParameter());
                    }
                }
            }
        }
    }

    private void pausar() {
        try {
            Thread.sleep(PAUSA_TRAS_UN_CORTE);
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            corriendo = false;
        }
    }
}
