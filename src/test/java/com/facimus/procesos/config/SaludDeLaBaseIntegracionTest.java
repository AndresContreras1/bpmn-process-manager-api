package com.facimus.procesos.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.awaitility.Awaitility.await;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import javax.sql.DataSource;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.test.context.ActiveProfiles;

import com.facimus.procesos.common.security.ContextoDeTienda.Alcance;
import com.facimus.procesos.config.SeguridadPorFilasConfig.DataSourceDeTienda;
import com.facimus.procesos.config.SeguridadPorFilasConfig.Topes;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

/**
 * PR 36: la salud de la base. Los topes de tiempo que recibe cada conexion de la API al asumir su rol, el aviso de una
 * conexion que no vuelve al pool, un indice detras de cada clave foranea y pg_stat_statements.
 */
@SpringBootTest
@ActiveProfiles("test")
class SaludDeLaBaseIntegracionTest {

    /** Las claves foraneas cuyas columnas no son el comienzo de ningun indice completo de su tabla. */
    private static final String CLAVES_SIN_INDICE = """
            select c.conrelid::regclass || '.' || c.conname
            from pg_constraint c
            where c.contype = 'f' and c.connamespace = to_regnamespace(current_schema())
              and not exists (
                  select 1 from pg_index i
                  where i.indrelid = c.conrelid and i.indpred is null
                    and (string_to_array(i.indkey::text, ' ')::int2[])[1:cardinality(c.conkey)] @> c.conkey
                    and (string_to_array(i.indkey::text, ' ')::int2[])[1:cardinality(c.conkey)] <@ c.conkey)
            order by 1
            """;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("Cada conexion de la API trae los topes de su rol: 30 segundos por sentencia y un minuto inactiva en "
            + "una transaccion")
    void conexionDeLaApi_traeLosTopesDeSuRol() {
        assertThat(jdbc.queryForObject("select current_user", String.class)).isEqualTo("procesos_app");
        assertThat(jdbc.queryForObject("show statement_timeout", String.class)).isEqualTo("30s");
        assertThat(jdbc.queryForObject("show idle_in_transaction_session_timeout", String.class)).isEqualTo("1min");
    }

    @Test
    @DisplayName("Los topes son del rol de la API: el dueno de las tablas, con el que migra Flyway, no tiene ninguno")
    void duenoDeLasTablas_noTieneTopes() throws SQLException {
        try (Connection dueno = conexionDelDueno();
                Statement sentencia = dueno.createStatement();
                ResultSet tope = sentencia.executeQuery("show statement_timeout")) {
            assertThat(tope.next()).isTrue();
            assertThat(tope.getString(1)).isEqualTo("0");
        }
    }

    @Test
    @DisplayName("Una sentencia que pasa su tope se cancela, y la conexion sigue sirviendo")
    void sentenciaQuePasaSuTope_seCancelaYLaConexionSigue() throws SQLException {
        DataSourceDeTienda conTope = new DataSourceDeTienda(sinPool(), Alcance.SISTEMA,
                new Topes(Duration.ofMillis(200), Duration.ZERO));

        try (Connection conexion = conTope.getConnection()) {
            JdbcTemplate unaConexion = new JdbcTemplate(new SingleConnectionDataSource(conexion, true));

            Throwable cancelada = catchThrowable(() -> unaConexion.execute("select pg_sleep(5)"));

            assertThat(NestedExceptionUtils.getMostSpecificCause(cancelada)).isInstanceOfSatisfying(SQLException.class,
                    causa -> assertThat(causa.getSQLState()).isEqualTo("57014"));
            assertThat(unaConexion.queryForObject("select 1", Integer.class)).isOne();
        }
    }

    @Test
    @DisplayName("Una transaccion que se queda abierta sin hacer nada pierde su conexion")
    void transaccionInactiva_pierdeSuConexion() throws SQLException {
        DataSourceDeTienda conTope = new DataSourceDeTienda(sinPool(), Alcance.SISTEMA,
                new Topes(Duration.ZERO, Duration.ofMillis(300)));

        try (Connection conexion = conTope.getConnection(); Connection observadora = conexionDelDueno()) {
            conexion.setAutoCommit(false);
            Integer proceso = new JdbcTemplate(new SingleConnectionDataSource(conexion, true))
                    .queryForObject("select pg_backend_pid()", Integer.class);
            JdbcTemplate mirar = new JdbcTemplate(new SingleConnectionDataSource(observadora, true));

            await().atMost(Duration.ofSeconds(10)).until(() -> mirar.queryForObject(
                    "select count(*) from pg_stat_activity where pid = ?", Integer.class, proceso) == 0);

            assertThat(conexion.isValid(2)).isFalse();
        }
    }

    @Test
    @DisplayName("El pool de la API avisa de una conexion que no vuelve en 20 segundos")
    void poolDeLaApi_avisaDeLasFugas() throws SQLException {
        assertThat(dataSource.unwrap(HikariDataSource.class).getLeakDetectionThreshold()).isEqualTo(20_000);
    }

    @Test
    @DisplayName("Una conexion que no vuelve al pool deja en el log un aviso con la pila de quien la tomo")
    void conexionQueNoVuelve_dejaUnAvisoConLaPila() throws SQLException {
        Logger registro = (Logger) LoggerFactory.getLogger("com.zaxxer.hikari.pool.ProxyLeakTask");
        ListAppender<ILoggingEvent> avisos = new ListAppender<>();
        avisos.start();
        registro.addAppender(avisos);
        HikariDataSource api = dataSource.unwrap(HikariDataSource.class);
        HikariConfig configuracion = new HikariConfig();
        configuracion.setPoolName("pool-con-una-fuga");
        configuracion.setJdbcUrl(api.getJdbcUrl());
        configuracion.setUsername(api.getUsername());
        configuracion.setPassword(api.getPassword());
        configuracion.setMaximumPoolSize(1);
        // Lo minimo que Hikari acepta; la API usa 20 segundos.
        configuracion.setLeakDetectionThreshold(2000);

        try (HikariDataSource pool = new HikariDataSource(configuracion); Connection _ = pool.getConnection()) {
            await().atMost(Duration.ofSeconds(10)).until(() -> !avisos.list.isEmpty());
        } finally {
            registro.detachAppender(avisos);
        }

        assertThat(avisos.list.getFirst()).satisfies(aviso -> {
            assertThat(aviso.getLevel()).isEqualTo(Level.WARN);
            assertThat(aviso.getFormattedMessage()).contains("Connection leak detection triggered");
            assertThat(aviso.getThrowableProxy().getStackTraceElementProxyArray())
                    .anySatisfy(linea -> assertThat(linea.getSTEAsString())
                            .contains("conexionQueNoVuelve_dejaUnAvisoConLaPila"));
        });
    }

    @Test
    @DisplayName("Cada clave foranea tiene un indice que empieza por sus columnas: borrar a quien apunta no lee la "
            + "tabla entera")
    void cadaClaveForanea_tieneSuIndice() {
        assertThat(jdbc.queryForObject("select count(*) from pg_constraint where contype = 'f' "
                + "and connamespace = to_regnamespace(current_schema())", Integer.class)).isGreaterThan(60);

        assertThat(jdbc.queryForList(CLAVES_SIN_INDICE, String.class)).isEmpty();
    }

    @Test
    @DisplayName("Las migraciones dejan pg_stat_statements, donde se mira que consulta se puso lenta")
    void lasMigracionesDejanPgStatStatements() {
        assertThat(jdbc.queryForObject("select count(*) from pg_extension where extname = 'pg_stat_statements'",
                Integer.class)).isOne();
    }

    /** Conexiones nuevas a la base del contexto, fuera del pool, para no devolverle una con otros topes. */
    private DataSource sinPool() throws SQLException {
        HikariDataSource api = dataSource.unwrap(HikariDataSource.class);
        return new DriverManagerDataSource(api.getJdbcUrl(), api.getUsername(), api.getPassword());
    }

    /** Una conexion como la de Flyway: el dueno de las tablas, sin asumir ningun rol. */
    private Connection conexionDelDueno() throws SQLException {
        HikariDataSource api = dataSource.unwrap(HikariDataSource.class);
        return DriverManager.getConnection(api.getJdbcUrl(), api.getUsername(), api.getPassword());
    }
}
