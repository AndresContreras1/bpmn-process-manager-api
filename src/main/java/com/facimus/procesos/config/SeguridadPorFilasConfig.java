package com.facimus.procesos.config;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Set;
import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DelegatingDataSource;

import com.facimus.procesos.common.security.ContextoDeTienda;
import com.facimus.procesos.common.security.ContextoDeTienda.Alcance;

/**
 * PR 51: el {@code DataSource} de la API pasa a PostgreSQL de quien es cada sentencia. Cada conexion que sale del pool
 * asume el rol {@code procesos_app}, que no es dueno de ninguna tabla ni salta la seguridad por filas, y antes de la
 * primera sentencia, y de cualquiera en que el alcance del hilo haya cambiado, fija {@code app.empresa_id} y
 * {@code app.sistema}. Las politicas de la V36 hacen el resto: una consulta que se olvide de filtrar por tienda no
 * ve, ni escribe, filas de otra.
 *
 * <p>Flyway no pasa por aqui: migra con su propia conexion, como el dueno de las tablas.
 */
@Configuration
public class SeguridadPorFilasConfig {

    static final String ROL = "procesos_app";

    /** Lo que vale cuando el hilo no dijo nada: NINGUNA en produccion, SISTEMA en las pruebas. */
    enum PorDefecto {
        NINGUNA,
        SISTEMA
    }

    @Bean
    static BeanPostProcessor seguridadPorFilas(@Value("${rls.contexto-por-defecto:NINGUNA}") PorDefecto porDefecto) {
        Alcance alcance = porDefecto == PorDefecto.SISTEMA ? Alcance.SISTEMA : Alcance.NINGUNA;
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String nombre) {
                return bean instanceof DataSource fuente && "dataSource".equals(nombre)
                        ? new DataSourceDeTienda(fuente, alcance) : bean;
            }
        };
    }

    /** El pool de siempre, con cada conexion envuelta. */
    static final class DataSourceDeTienda extends DelegatingDataSource {

        private final Alcance porDefecto;

        DataSourceDeTienda(DataSource pool, Alcance porDefecto) {
            super(pool);
            this.porDefecto = porDefecto;
        }

        @Override
        public Connection getConnection() throws SQLException {
            return envolver(super.getConnection());
        }

        @Override
        public Connection getConnection(String usuario, String clave) throws SQLException {
            return envolver(super.getConnection(usuario, clave));
        }

        private Connection envolver(Connection conexion) {
            return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                    new Class<?>[] {Connection.class}, new ConexionDeTienda(conexion, porDefecto));
        }
    }

    /**
     * Antes de preparar una sentencia, mira si el alcance del hilo es el que la conexion ya tiene; si no, lo fija, en
     * una sola ida y vuelta. Es por sesion y no por transaccion, porque la conexion puede seguir dentro de la misma
     * transaccion cuando el alcance cambia, como en un receptor de eventos.
     */
    private static final class ConexionDeTienda implements InvocationHandler {

        private static final Set<String> SENTENCIAS = Set.of("prepareStatement", "createStatement", "prepareCall");

        private final Connection conexion;
        private final Alcance porDefecto;
        private Alcance fijado;

        ConexionDeTienda(Connection conexion, Alcance porDefecto) {
            this.conexion = conexion;
            this.porDefecto = porDefecto;
        }

        @Override
        public Object invoke(Object proxy, Method metodo, Object[] argumentos) throws Throwable {
            if (SENTENCIAS.contains(metodo.getName())) {
                Alcance alcance = ContextoDeTienda.actual().orElse(porDefecto);
                if (!Objects.equals(alcance, fijado)) {
                    fijar(alcance);
                }
            }
            try {
                return metodo.invoke(conexion, argumentos);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }

        private void fijar(Alcance alcance) throws SQLException {
            try (PreparedStatement fijacion = conexion.prepareStatement("select set_config('role', '" + ROL
                    + "', false), set_config('app.empresa_id', ?, false), set_config('app.sistema', ?, false)")) {
                if (alcance.empresaId() == null) {
                    fijacion.setString(1, "");
                } else {
                    fijacion.setString(1, alcance.empresaId().toString());
                }
                fijacion.setString(2, alcance.sistema() ? "on" : "off");
                fijacion.execute();
            }
            fijado = alcance;
        }
    }
}
