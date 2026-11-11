package com.facimus.procesos.modelado.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.postgresql.PGConnection;
import org.postgresql.PGNotification;
import org.slf4j.LoggerFactory;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

/**
 * PR 58: la escucha en vivo de cada replica. Abre su propia conexion con los datos de la base, hace {@code LISTEN} en
 * el canal y le pasa cada aviso al canal, sin dar el latido antes de tiempo; si la conexion se corta lo avisa en el
 * log y espera antes de reabrirla, y una interrupcion durante esa espera la detiene. Detenerla no se traga la
 * interrupcion de quien la detiene.
 */
@ExtendWith(MockitoExtension.class)
class EscuchaDelEnVivoTest {

    /** Un driver de JDBC de prueba: da la conexion simulada, o falla, y anota quien lo llamo y con que. */
    private static final class DriverDePrueba implements Driver {

        static final String URL = "jdbc:escucha-del-en-vivo:prueba";

        private final AtomicInteger intentos = new AtomicInteger();
        private final CountDownLatch llamado = new CountDownLatch(1);
        private volatile Connection conexion;
        private volatile SQLException falla;
        private volatile Thread hilo;
        private volatile Properties datos;

        @Override
        public Connection connect(String url, Properties info) throws SQLException {
            if (!acceptsURL(url)) {
                return null;
            }
            hilo = Thread.currentThread();
            datos = info;
            intentos.incrementAndGet();
            llamado.countDown();
            if (falla != null) {
                throw falla;
            }
            return conexion;
        }

        @Override
        public boolean acceptsURL(String url) {
            return URL.equals(url);
        }

        @Override
        public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) {
            return new DriverPropertyInfo[0];
        }

        @Override
        public int getMajorVersion() {
            return 1;
        }

        @Override
        public int getMinorVersion() {
            return 0;
        }

        @Override
        public boolean jdbcCompliant() {
            return false;
        }

        @Override
        public java.util.logging.Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException();
        }
    }

    @Mock
    private JdbcConnectionDetails base;
    @Mock
    private CanalEnVivo canal;
    @Mock
    private Connection conexion;
    @Mock
    private Statement sentencia;

    private final DriverDePrueba driver = new DriverDePrueba();
    private final Logger logger = (Logger) LoggerFactory.getLogger(EscuchaDelEnVivo.class);
    private final ListAppender<ILoggingEvent> log = new ListAppender<>();
    private EscuchaDelEnVivo escucha;

    @BeforeEach
    void setUp() throws SQLException {
        DriverManager.registerDriver(driver);
        log.start();
        logger.addAppender(log);
        when(base.getJdbcUrl()).thenReturn(DriverDePrueba.URL);
        when(base.getUsername()).thenReturn("api");
        when(base.getPassword()).thenReturn("secreta");
        escucha = new EscuchaDelEnVivo(base, canal);
    }

    @AfterEach
    void tearDown() throws SQLException {
        if (escucha.isRunning()) {
            escucha.stop();
        }
        logger.detachAppender(log);
        DriverManager.deregisterDriver(driver);
    }

    /** La conexion de la base: el primer pedido trae dos avisos, y despues espera un poco sin traer nada. */
    private void conexionConDosAvisos() throws SQLException {
        PGNotification uno = mock(PGNotification.class);
        PGNotification dos = mock(PGNotification.class);
        when(uno.getParameter()).thenReturn("aviso-1");
        when(dos.getParameter()).thenReturn("aviso-2");
        // Sin registro de llamadas: el bucle pide avisos muchas veces
        PGConnection postgres = mock(PGConnection.class, withSettings().stubOnly().strictness(Strictness.LENIENT));
        AtomicInteger pedidos = new AtomicInteger();
        when(postgres.getNotifications(500)).thenAnswer(inv -> {
            if (pedidos.getAndIncrement() == 0) {
                return new PGNotification[] {uno, dos};
            }
            Thread.sleep(5);
            return null;
        });
        when(conexion.createStatement()).thenReturn(sentencia);
        when(conexion.unwrap(PGConnection.class)).thenReturn(postgres);
        driver.conexion = conexion;
    }

    @Test
    @DisplayName("Escucha el canal en vivo con su propia conexion y le pasa cada aviso, sin latir antes de tiempo")
    void escucha_pasaCadaAvisoAlCanal() throws SQLException {
        conexionConDosAvisos();
        assertThat(escucha.isRunning()).isFalse();

        escucha.start();

        assertThat(escucha.isRunning()).isTrue();
        InOrder orden = Mockito.inOrder(canal);
        orden.verify(canal, timeout(2000)).recibir("aviso-1");
        orden.verify(canal, timeout(2000)).recibir("aviso-2");
        verify(sentencia).execute("LISTEN en_vivo");
        verify(sentencia).close();
        assertThat(driver.datos.getProperty("user")).isEqualTo("api");
        assertThat(driver.datos.getProperty("password")).isEqualTo("secreta");
        // El latido es cada 25 segundos, no al arrancar
        verify(canal, never()).latir();

        escucha.stop();

        assertThat(escucha.isRunning()).isFalse();
        verify(conexion, timeout(2000)).close();
        assertThat(driver.intentos.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("Si la conexion se corta lo avisa en el log y espera antes de reabrir; una interrupcion la detiene")
    void corte_avisaYEsperaAntesDeReabrir() throws Exception {
        driver.falla = new SQLException("la base se reinicio");

        escucha.start();

        assertThat(driver.llamado.await(2, TimeUnit.SECONDS)).isTrue();
        await().atMost(Duration.ofSeconds(2)).until(() -> !log.list.isEmpty());
        ILoggingEvent aviso = log.list.getFirst();
        assertThat(aviso.getLevel()).isEqualTo(Level.WARN);
        assertThat(aviso.getFormattedMessage()).isEqualTo("Se corto la escucha en vivo; se reabre en PT5S");
        driver.hilo.interrupt();
        await().atMost(Duration.ofSeconds(2)).until(() -> !escucha.isRunning());
        // No reintento en seguida: esperaba los cinco segundos cuando la interrumpieron
        assertThat(driver.intentos.get()).isEqualTo(1);
        verify(canal, never()).recibir(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("Detener la escucha desde un hilo interrumpido conserva su interrupcion")
    void stop_conservaLaInterrupcionDeQuienLaDetiene() throws SQLException {
        conexionConDosAvisos();
        escucha.start();
        verify(canal, timeout(2000)).recibir("aviso-2");

        Thread.currentThread().interrupt();
        escucha.stop();

        assertThat(Thread.interrupted()).isTrue();
        assertThat(escucha.isRunning()).isFalse();
        verify(conexion, timeout(2000)).close();
    }
}
