package com.facimus.procesos.config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.postgresql.PGConnection;
import org.postgresql.PGNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import com.facimus.procesos.security.AvisoDeSesionesCerradas;
import com.facimus.procesos.security.RevokedSessions;

/**
 * D34: escucha en PostgreSQL los avisos de sesiones cerradas que manda cualquier instancia de la API y las revoca en
 * esta. Con varias instancias, una sesion cerrada en una deja de servir en todas al momento, sin esperar a que venza
 * su token.
 * <p>
 * Usa una conexion propia, fuera del pool, porque LISTEN la ocupa mientras la aplicacion vive. Si se corta, se reabre;
 * al abrirse, y despues cada cinco minutos, vuelve a leer de la base las sesiones cerradas que todavia importan, por
 * si alguna se cerro mientras nadie escuchaba. En el perfil test no corre, porque una prueba no puede tener nada por
 * detras; la de dos instancias la enciende.
 */
@Component
@ConditionalOnProperty(name = "sesiones.escuchar-cierres", havingValue = "true", matchIfMissing = true)
public class EscuchaDeSesionesCerradas implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(EscuchaDeSesionesCerradas.class);
    private static final int ESPERA_POR_AVISOS_MS = 500;
    private static final Duration REPASO = Duration.ofMinutes(5);
    private static final Duration PAUSA_TRAS_UN_CORTE = Duration.ofSeconds(5);

    private final JdbcConnectionDetails base;
    private final RevokedSessions revocadas;
    private volatile boolean corriendo;
    private Thread hilo;

    public EscuchaDeSesionesCerradas(JdbcConnectionDetails base, RevokedSessions revocadas) {
        this.base = base;
        this.revocadas = revocadas;
    }

    @Override
    public void start() {
        corriendo = true;
        hilo = Thread.ofVirtual().name("escucha-de-sesiones-cerradas").start(this::escuchar);
    }

    /** La espera por avisos dura medio segundo: al apagarse, el hilo termina solo en ese tiempo. */
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
                    sentencia.execute("LISTEN " + AvisoDeSesionesCerradas.CANAL);
                }
                recibirAvisos(conexion.unwrap(PGConnection.class));
            } catch (SQLException | RuntimeException corte) {
                if (corriendo) {
                    log.warn("Se corto la escucha de las sesiones cerradas; se reabre en {}", PAUSA_TRAS_UN_CORTE,
                            corte);
                    pausar();
                }
            }
        }
    }

    private void recibirAvisos(PGConnection postgres) throws SQLException {
        // Lo primero es repasar: entre el arranque y este LISTEN pudo cerrarse alguna sesion.
        Instant proximoRepaso = Instant.now();
        while (corriendo) {
            if (!Instant.now().isBefore(proximoRepaso)) {
                revocadas.recuperarCerradas();
                proximoRepaso = Instant.now().plus(REPASO);
            }
            PGNotification[] avisos = postgres.getNotifications(ESPERA_POR_AVISOS_MS);
            if (avisos != null) {
                for (PGNotification aviso : avisos) {
                    revocadas.revocar(List.of(aviso.getParameter().split(",")));
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
