package com.facimus.procesos.postgres;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * El motor de produccion, en un contenedor, para las clases marcadas con {@link ConPostgresReal}.
 * <p>
 * Los tests de todos los dias corren sobre H2, que arranca en milisegundos, pero hay cosas que H2 no sabe hacer y
 * que la aplicacion si le pide a PostgreSQL: los indices unicos parciales de la V2 y la V8, la columna {@code text}
 * de la V13 y el esquema que Hibernate valida al arrancar. Esas se prueban aqui, contra la misma imagen que levanta
 * compose.yaml.
 * <p>
 * {@code @ServiceConnection} apunta el datasource al contenedor, de modo que Flyway aplica {@code common} y
 * {@code postgresql} sin que haya que escribir una sola propiedad. Spring guarda en cache cada contexto de prueba,
 * asi que las clases que comparten configuracion comparten tambien el contenedor.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresDePrueba {

    /** La misma version que corre en produccion: compose.yaml declara postgres:16. */
    private static final String IMAGEN = "postgres:16";

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer(IMAGEN);
    }

    /**
     * Sin Docker no hay motor real. Estas pruebas no son un requisito para trabajar en el proyecto: quien no tenga
     * Docker las ve saltadas en vez de rotas, y el trabajo del CI que si lo tiene las corre siempre.
     */
    public static boolean hayDocker() {
        return DockerClientFactory.instance().isDockerAvailable();
    }
}
