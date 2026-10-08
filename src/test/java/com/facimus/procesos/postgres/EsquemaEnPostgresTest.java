package com.facimus.procesos.postgres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.OptionalInt;
import javax.sql.DataSource;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * El esquema que Flyway deja en PostgreSQL, leido del catalogo del propio motor: lo que las entidades no dicen y la
 * aplicacion necesita, como los indices parciales y las columnas text.
 */
@SpringBootTest
@ActiveProfiles("test")
class EsquemaEnPostgresTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("El contexto arranca contra PostgreSQL y Hibernate da el esquema por bueno")
    void elMotorEsPostgres_yElEsquemaValida() throws SQLException {
        // ddl-auto=validate corre al arrancar: si una entidad no cuadrara con la tabla, no habria contexto.
        try (Connection conexion = dataSource.getConnection()) {
            DatabaseMetaData motor = conexion.getMetaData();

            assertThat(motor.getDatabaseProductName()).isEqualTo("PostgreSQL");
        }
    }

    @Test
    @DisplayName("La version del motor es la de produccion, o la que declara el servidor externo de la corrida")
    void laVersionDelMotorEsLaEsperada() throws SQLException {
        OptionalInt esperada = PostgresDePrueba.versionMayorEsperada();
        assumeTrue(esperada.isPresent(), "El servidor externo no declara PRUEBAS_POSTGRES_VERSION_MAYOR");
        try (Connection conexion = dataSource.getConnection()) {
            assertThat(conexion.getMetaData().getDatabaseMajorVersion()).isEqualTo(esperada.getAsInt());
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
    }

    @Test
    @DisplayName("V8: un arco dado de baja no ocupa su par de nodos porque el indice es parcial")
    void v8_elIndiceDeArcosEsParcial() {
        assertThat(definicionDelIndice("uk_arcos_origen_destino_activo"))
                .contains("UNIQUE")
                .contains("(origen_id, destino_id)")
                .endsWith("WHERE activo");
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

    @Test
    @DisplayName("V20: el cuerpo de un mensaje es text en las dos bandejas, y siempre viene lleno")
    void v20_losCuerposDeLosMensajesSonText() {
        assertThat(tipoDeColumna("mensajes_salientes", "cuerpo")).isEqualTo("text NO");
        assertThat(tipoDeColumna("mensajes_entrantes", "cuerpo")).isEqualTo("text NO");
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
}
