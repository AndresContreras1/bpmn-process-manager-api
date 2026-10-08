package com.facimus.procesos.postgres;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.OptionalInt;
import java.util.Properties;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import org.flywaydb.core.Flyway;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * El PostgreSQL de las pruebas: uno solo para toda la corrida, el mismo motor que corre en produccion.
 * <p>
 * Lo normal es un contenedor de la imagen que declara compose.yaml, que Testcontainers levanta la primera vez que un
 * contexto pide una base y que se va con la JVM. Donde no hay contenedores de Linux, como en los runners de Windows
 * del CI, {@code PRUEBAS_POSTGRES_URL} (la URL JDBC de una base cualquiera del servidor), {@code
 * PRUEBAS_POSTGRES_USUARIO} y {@code PRUEBAS_POSTGRES_CLAVE} apuntan a un servidor que ya esta corriendo, y {@code
 * PRUEBAS_POSTGRES_VERSION_MAYOR} dice que version es.
 * <p>
 * Las migraciones corren una sola vez, en una base plantilla. Cada contexto de Spring recibe una copia propia, que
 * PostgreSQL hace en milisegundos: las pruebas no comparten datos, igual que cuando cada contexto tenia su H2.
 */
public final class PostgresDePrueba {

    /** La misma version que corre en produccion: compose.yaml declara postgres:16. */
    static final String IMAGEN = "postgres:16";
    private static final int VERSION_DE_LA_IMAGEN = 16;

    /** Cada contexto tiene su pool; con decenas de contextos en cache, cien conexiones se quedarian cortas. */
    private static final String MAXIMO_DE_CONEXIONES = "max_connections=300";

    /** Distingue las bases de esta JVM de las de otra que use el mismo servidor externo. */
    private static final String SUFIJO = Long.toString(ProcessHandle.current().pid(), 36) + "_"
            + Integer.toString(ThreadLocalRandom.current().nextInt(1 << 20), 36);
    private static final AtomicInteger BASES_CREADAS = new AtomicInteger();

    private static Servidor servidor;
    private static String plantilla;

    private PostgresDePrueba() {
    }

    /** El servidor de la corrida; el primero que lo pide lo arranca. */
    static synchronized Servidor servidor() {
        if (servidor == null) {
            servidor = arrancar();
        }
        return servidor;
    }

    /**
     * Una base nueva para un contexto, copiada de la plantilla ya migrada. Flyway la encuentra al dia cuando el
     * contexto arranca, y solo comprueba que las migraciones no hayan cambiado.
     */
    static synchronized String nuevaBase() {
        if (plantilla == null) {
            plantilla = crearPlantilla();
        }
        String nombre = "prueba_" + SUFIJO + "_" + BASES_CREADAS.incrementAndGet();
        ejecutar("create database " + nombre + " template " + plantilla);
        return servidor().urlDe(nombre);
    }

    /** La version mayor del servidor, si se sabe cual se espera: la de la imagen, o la que declara el entorno. */
    public static OptionalInt versionMayorEsperada() {
        String declarada = System.getenv("PRUEBAS_POSTGRES_VERSION_MAYOR");
        if (servidor().externo()) {
            return declarada == null || declarada.isBlank() ? OptionalInt.empty()
                    : OptionalInt.of(Integer.parseInt(declarada.trim()));
        }
        return OptionalInt.of(VERSION_DE_LA_IMAGEN);
    }

    private static Servidor arrancar() {
        String url = System.getenv("PRUEBAS_POSTGRES_URL");
        if (url != null && !url.isBlank()) {
            return new Servidor(url.trim(), System.getenv("PRUEBAS_POSTGRES_USUARIO"),
                    System.getenv("PRUEBAS_POSTGRES_CLAVE"), true);
        }
        // fsync apagado es lo que Testcontainers pone por defecto: una base de pruebas no tiene que sobrevivir a nada.
        PostgreSQLContainer contenedor = new PostgreSQLContainer(IMAGEN)
                .withCommand("postgres", "-c", "fsync=off", "-c", MAXIMO_DE_CONEXIONES);
        try {
            contenedor.start();
        } catch (RuntimeException sinDocker) {
            throw new IllegalStateException("Las pruebas corren contra PostgreSQL: hace falta Docker en marcha, o "
                    + "PRUEBAS_POSTGRES_URL apuntando a un servidor (ver docs/testing.md).", sinDocker);
        }
        return new Servidor(contenedor.getJdbcUrl(), contenedor.getUsername(), contenedor.getPassword(), false);
    }

    /** La plantilla, migrada con las mismas ubicaciones que usa la aplicacion. */
    private static String crearPlantilla() {
        String nombre = "plantilla_" + SUFIJO;
        ejecutar("create database " + nombre);
        Flyway.configure()
                .dataSource(servidor().urlDe(nombre), servidor().usuario(), servidor().clave())
                .locations(ubicacionesDeLasMigraciones())
                .load()
                .migrate();
        return nombre;
    }

    /** Las de application.properties, con el motor puesto donde Spring Boot lo pondria. */
    private static String[] ubicacionesDeLasMigraciones() {
        try {
            Properties aplicacion = PropertiesLoaderUtils.loadProperties(new ClassPathResource("application.properties"));
            return Arrays.stream(aplicacion.getProperty("spring.flyway.locations").split(","))
                    .map(ubicacion -> ubicacion.trim().replace("{vendor}", "postgresql"))
                    .toArray(String[]::new);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void ejecutar(String sql) {
        Servidor actual = servidor();
        try (Connection conexion = DriverManager.getConnection(actual.url(), actual.usuario(), actual.clave());
                Statement sentencia = conexion.createStatement()) {
            sentencia.execute(sql);
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo ejecutar en el PostgreSQL de las pruebas: " + sql, e);
        }
    }

    /** Donde esta el servidor: la URL de una base cualquiera de el y las credenciales. */
    record Servidor(String url, String usuario, String clave, boolean externo) {

        /** La misma URL, con otra base y las mismas opciones. */
        String urlDe(String base) {
            int opciones = url.indexOf('?');
            String sinOpciones = opciones < 0 ? url : url.substring(0, opciones);
            return sinOpciones.substring(0, sinOpciones.lastIndexOf('/') + 1) + base
                    + (opciones < 0 ? "" : url.substring(opciones));
        }
    }
}
