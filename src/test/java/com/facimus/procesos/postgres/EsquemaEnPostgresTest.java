package com.facimus.procesos.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * El esquema que Flyway deja en PostgreSQL, leido del catalogo del propio motor.
 * <p>
 * Las migraciones se parten en dos por motor porque H2 no tiene indices parciales ni sobre expresiones: donde
 * PostgreSQL escribe {@code where activo}, H2 usa una columna calculada. La rama de H2 la prueban las suites de
 * todos los dias; la de PostgreSQL solo se puede probar aqui, y sin esta clase seria codigo que nadie ejecuta
 * hasta que arranca produccion.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(PostgresDePrueba.class)
@ConPostgresReal
class EsquemaEnPostgresTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("El contexto arranca contra PostgreSQL 16 y Hibernate da el esquema por bueno")
    void elMotorEsPostgres16_yElEsquemaValida() throws SQLException {
        // ddl-auto=validate corre al arrancar: si una entidad no cuadrara con la tabla, no habria contexto.
        try (Connection conexion = dataSource.getConnection()) {
            DatabaseMetaData motor = conexion.getMetaData();

            assertThat(motor.getDatabaseProductName()).isEqualTo("PostgreSQL");
            assertThat(motor.getDatabaseMajorVersion()).isEqualTo(16);
        }
    }

    @Test
    @DisplayName("V2: los nombres unicos por tienda son indices parciales sobre lower(nombre), no columnas calculadas")
    void v2_losIndicesDeNombresSonParcialesYSobreLower() {
        assertThat(definicionDelIndice("uk_procesos_empresa_nombre_activo"))
                .contains("UNIQUE")
                .contains("lower(")
                .endsWith("WHERE activo");
        assertThat(definicionDelIndice("uk_roles_proceso_empresa_nombre_activo"))
                .contains("UNIQUE")
                .contains("lower(")
                .endsWith("WHERE activo");
        // La columna calculada es el recurso de H2: aqui no existe, asi que Flyway aplico la rama del motor correcto.
        assertThat(hayColumna("procesos", "nombre_activo")).isFalse();
        assertThat(hayColumna("roles_proceso", "nombre_activo")).isFalse();
    }

    @Test
    @DisplayName("V8: un arco dado de baja no ocupa su par de nodos porque el indice es parcial")
    void v8_elIndiceDeArcosEsParcial() {
        assertThat(definicionDelIndice("uk_arcos_origen_destino_activo"))
                .contains("UNIQUE")
                .contains("(origen_id, destino_id)")
                .endsWith("WHERE activo");
        assertThat(hayColumna("arcos", "origen_activo")).isFalse();
    }

    @Test
    @DisplayName("V13: el diagrama publicado se guarda en un text obligatorio, sin el tope de un varchar")
    void v13_laDefinicionDeLaVersionEsText() {
        assertThat(tipoDeColumna("versiones_proceso", "definicion")).isEqualTo("text NO");
    }

    @Test
    @DisplayName("V16: las variables de un caso y los datos de una tarea tambien son text, no objetos grandes")
    void v16_lasVariablesDelCasoSonText() {
        assertThat(tipoDeColumna("casos", "variables")).isEqualTo("text NO");
        assertThat(tipoDeColumna("actividades_caso", "datos_salida")).isEqualTo("text YES");
    }

    /** El tipo de una columna y si acepta nulos, como los declara el catalogo del motor. */
    private String tipoDeColumna(String tabla, String columna) {
        return jdbcTemplate.queryForObject("""
                select data_type || ' ' || is_nullable from information_schema.columns
                where table_name = ? and column_name = ?
                """, String.class, tabla, columna);
    }

    private String definicionDelIndice(String nombre) {
        return jdbcTemplate.queryForObject(
                "select indexdef from pg_indexes where schemaname = current_schema() and indexname = ?",
                String.class, nombre);
    }

    private boolean hayColumna(String tabla, String columna) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject("""
                select exists (select 1 from information_schema.columns
                where table_schema = current_schema() and table_name = ? and column_name = ?)
                """, Boolean.class, tabla, columna));
    }
}
