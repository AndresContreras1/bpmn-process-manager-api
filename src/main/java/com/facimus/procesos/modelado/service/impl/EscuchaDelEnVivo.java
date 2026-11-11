package com.facimus.procesos.modelado.service.impl;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;

import org.postgresql.PGConnection;
import org.postgresql.PGNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * PR 58: escucha los avisos en vivo con una conexion propia, fuera del pool, y da el latido de esta replica. No es un
 * {@code @Scheduled}: esos corren en una sola replica, y el latido es de cada una.
 */
@Component
@ConditionalOnProperty(name = "modelado.en-vivo.escuchar", havingValue = "true", matchIfMissing = true)
public class EscuchaDelEnVivo implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(EscuchaDelEnVivo.class);
    private static final int ESPERA_POR_AVISOS_MS = 500;
    private static final Duration LATIDO = Duration.ofSeconds(25);
    private static final Duration PAUSA_TRAS_UN_CORTE = Duration.ofSeconds(5);

    private final JdbcConnectionDetails base;
    private final CanalEnVivo canal;
    private volatile boolean corriendo;
    private Thread hilo;

    public EscuchaDelEnVivo(JdbcConnectionDetails base, CanalEnVivo canal) {
        this.base = base;
        this.canal = canal;
    }

    @Override
    public void start() {
        corriendo = true;
        hilo = Thread.ofVirtual().name("escucha-del-en-vivo").start(this::escuchar);
    }

    @Override
    public void stop() {
        corriendo = false;
        try {
            hilo.join(Duration.ofSeconds(2));
        } catch (InterruptedException interrumpido) {
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
                    sentencia.execute("LISTEN " + CanalEnVivo.CANAL);
                }
                recibirAvisos(conexion.unwrap(PGConnection.class));
            } catch (SQLException | RuntimeException corte) {
                if (corriendo) {
                    log.warn("Se corto la escucha en vivo; se reabre en {}", PAUSA_TRAS_UN_CORTE, corte);
                    pausar();
                }
            }
        }
    }

    private void recibirAvisos(PGConnection postgres) throws SQLException {
        Instant proximoLatido = Instant.now().plus(LATIDO);
        while (corriendo) {
            if (!Instant.now().isBefore(proximoLatido)) {
                canal.latir();
                proximoLatido = Instant.now().plus(LATIDO);
            }
            PGNotification[] avisos = postgres.getNotifications(ESPERA_POR_AVISOS_MS);
            if (avisos != null) {
                for (PGNotification aviso : avisos) {
                    canal.recibir(aviso.getParameter());
                }
            }
        }
    }

    private void pausar() {
        try {
            Thread.sleep(PAUSA_TRAS_UN_CORTE);
        } catch (InterruptedException interrumpido) {
            Thread.currentThread().interrupt();
            corriendo = false;
        }
    }
}
