package com.facimus.procesos.postgres;

import java.util.List;
import java.util.Map;

import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.test.context.ContextConfigurationAttributes;
import org.springframework.test.context.ContextCustomizer;
import org.springframework.test.context.ContextCustomizerFactory;
import org.springframework.test.context.MergedContextConfiguration;

/**
 * Le da a cada contexto de Spring de las pruebas su propia base en el PostgreSQL de la corrida
 * ({@link PostgresDePrueba}). Se registra en {@code META-INF/spring.factories}, asi que llega a cada contexto sin que
 * ninguna prueba lo pida.
 * <p>
 * La base se crea cuando el contexto la pide por primera vez: un slice web, que no tiene datasource, no crea ninguna
 * ni arranca el contenedor.
 */
public class BaseDePruebaPorContexto implements ContextCustomizerFactory {

    @Override
    public ContextCustomizer createContextCustomizer(Class<?> testClass,
            List<ContextConfigurationAttributes> configAttributes) {
        return new Personalizador();
    }

    /** Igual en todos los contextos, para no partir la cache de contextos de Spring. */
    private static final class Personalizador implements ContextCustomizer {

        @Override
        public void customizeContext(ConfigurableApplicationContext context, MergedContextConfiguration config) {
            MutablePropertySources fuentes = context.getEnvironment().getPropertySources();
            fuentes.addFirst(new BaseDelContexto());
            // Por debajo de todo: un perfil que fija su pool, como prod, lo conserva.
            fuentes.addLast(new MapPropertySource("pool de las pruebas", Map.of(
                    "spring.datasource.hikari.maximum-pool-size", "5",
                    "spring.datasource.hikari.minimum-idle", "0",
                    "spring.datasource.hikari.idle-timeout", "10000")));
        }

        @Override
        public boolean equals(Object otro) {
            return otro instanceof Personalizador;
        }

        @Override
        public int hashCode() {
            return Personalizador.class.hashCode();
        }
    }

    /**
     * La conexion del contexto. La base se crea la primera vez que alguien pregunta por la URL, y la respuesta queda
     * fija para todo el contexto.
     */
    private static final class BaseDelContexto extends PropertySource<Object> {

        private String url;

        BaseDelContexto() {
            super("base de prueba del contexto");
        }

        @Override
        public Object getProperty(String nombre) {
            return switch (nombre) {
                case "spring.datasource.url" -> url();
                case "spring.datasource.username" -> PostgresDePrueba.servidor().usuario();
                case "spring.datasource.password" -> PostgresDePrueba.servidor().clave();
                // Los slices de JPA conservan esta base en vez de buscar una embebida, que ya no hay.
                case "spring.test.database.replace" -> "none";
                default -> null;
            };
        }

        private synchronized String url() {
            if (url == null) {
                url = PostgresDePrueba.nuevaBase();
            }
            return url;
        }
    }
}
