package com.facimus.procesos.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.test.context.ActiveProfiles;

import com.facimus.procesos.common.security.ContextoDeTienda;
import com.facimus.procesos.common.security.ContextoDeTienda.Alcance;
import com.facimus.procesos.gestion.service.EmpresaService;
import com.facimus.procesos.gestion.service.ProcesoService;

/**
 * PR 51: la seguridad por filas de PostgreSQL. Cada tabla con empresa_id tiene su politica y la base la hace cumplir
 * aunque la consulta se olvide de filtrar; la API entra con un rol que no es dueno de nada ni la salta; y lo que no
 * dice de que tienda es, en produccion, no ve ninguna fila.
 */
@SpringBootTest
@ActiveProfiles("test")
class SeguridadPorFilasIntegracionTest {

    private static final String CLAVE = "marea-violeta-del-sur";
    private static int tiendas;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private EmpresaService empresaService;

    @Autowired
    private ProcesoService procesoService;

    private Long una;
    private Long otra;

    @BeforeEach
    void dosTiendas() {
        una = tienda("Una");
        otra = tienda("Otra");
    }

    @Test
    @DisplayName("Cada tabla con empresa_id tiene la seguridad por filas encendida, forzada y con su politica")
    void cadaTablaConEmpresaTieneSuPolitica() {
        List<Map<String, Object>> tablas = jdbc.queryForList("""
                select c.relname as tabla, c.relrowsecurity as encendida, c.relforcerowsecurity as forzada,
                       exists (select 1 from pg_policies p where p.tablename = c.relname
                               and p.policyname = 'aislamiento_por_tienda') as con_politica
                from information_schema.columns col
                join pg_class c on c.relname = col.table_name and c.relnamespace = to_regnamespace(current_schema())
                where col.table_schema = current_schema() and col.column_name = 'empresa_id'
                """);

        assertThat(tablas).hasSizeGreaterThan(25).allSatisfy(tabla -> assertThat(tabla)
                .as("%s", tabla.get("tabla"))
                .containsEntry("encendida", true)
                .containsEntry("forzada", true)
                .containsEntry("con_politica", true));
    }

    @Test
    @DisplayName("La API entra como procesos_app: no es superusuario, no salta la seguridad y no es duena de ninguna tabla")
    void elRolDeLaApi() {
        assertThat(jdbc.queryForObject("select current_user", String.class)).isEqualTo("procesos_app");
        assertThat(jdbc.queryForMap("select rolsuper, rolbypassrls from pg_roles where rolname = current_user"))
                .containsEntry("rolsuper", false).containsEntry("rolbypassrls", false);
        assertThat(jdbc.queryForObject("select count(*) from pg_tables where tableowner = current_user",
                Integer.class)).isZero();
    }

    @Test
    @DisplayName("Una consulta escrita mal a proposito, sin empresa_id, solo ve las filas de la tienda de quien pide")
    void consultaSinFiltroNoVeLoAjeno() {
        int totales = jdbc.queryForObject("select count(*) from procesos where empresa_id in (?, ?)", Integer.class,
                una, otra);
        assertThat(totales).isEqualTo(3);

        ContextoDeTienda.deLaTienda(una, () -> {
            assertThat(jdbc.queryForList("select distinct empresa_id from procesos", Long.class)).containsExactly(una);
            assertThat(jdbc.queryForList("select distinct empresa_id from usuarios", Long.class)).containsExactly(una);
            assertThat(jdbc.queryForObject("select count(*) from historial_cambios where empresa_id = ?",
                    Integer.class, otra)).isZero();
        });
    }

    @Test
    @DisplayName("Ni cambia ni borra filas de otra tienda, ni le agrega una: la politica las esconde y las rechaza")
    void noEscribeLoAjeno() {
        ContextoDeTienda.deLaTienda(una, () -> {
            assertThat(jdbc.update("update procesos set nombre = 'Cambiado' where empresa_id = ?", otra)).isZero();
            assertThat(jdbc.update("delete from claves_idempotencia where empresa_id = ?", otra)).isZero();
            Throwable rechazo = org.assertj.core.api.Assertions.catchThrowable(() -> jdbc.update(
                    "insert into trabajos (empresa_id, tipo, datos, estado, intentos, maximo_intentos, disponible_desde, "
                            + "creado_en) values (?, 'PRUEBA', '{}', 'PENDIENTE', 0, 1, now(), now())", otra));
            assertThat(rechazo).hasStackTraceContaining("row-level security");
        });
        assertThat(jdbc.queryForObject("select count(*) from procesos where empresa_id = ? and nombre = 'Cambiado'",
                Integer.class, otra)).isZero();
    }

    @Test
    @DisplayName("Sin decir de que tienda es, en produccion no se ve ninguna fila; el sistema, todas")
    void porDefectoEnProduccionNingunaFila() {
        DataSource pool = ((DelegatingDataSource) dataSource).getTargetDataSource();
        JdbcTemplate comoEnProduccion = new JdbcTemplate(
                new SeguridadPorFilasConfig.DataSourceDeTienda(pool, Alcance.NINGUNA,
                        SeguridadPorFilasConfig.Topes.NINGUNO));

        assertThat(comoEnProduccion.queryForObject("select count(*) from usuarios", Integer.class)).isZero();
        assertThat(ContextoDeTienda.comoSistema(() -> comoEnProduccion.queryForObject(
                "select count(*) from usuarios where empresa_id in (?, ?)", Integer.class, una, otra))).isEqualTo(2);
        assertThat(ContextoDeTienda.deLaTienda(otra, () -> comoEnProduccion.queryForList(
                "select distinct empresa_id from usuarios", Long.class))).containsExactly(otra);
    }

    @Test
    @DisplayName("El alcance se fija otra vez cuando cambia dentro de la misma conexion")
    void cambiaDentroDeLaMismaConexion() throws java.sql.SQLException {
        DataSource pool = ((DelegatingDataSource) dataSource).getTargetDataSource();
        // La conexion vuelve al pool al terminar: el pool avisa de la que no vuelve (PR 36).
        try (java.sql.Connection conexion = new SeguridadPorFilasConfig.DataSourceDeTienda(pool, Alcance.NINGUNA,
                SeguridadPorFilasConfig.Topes.NINGUNO).getConnection()) {
            JdbcTemplate unaSolaConexion = new JdbcTemplate(
                    new org.springframework.jdbc.datasource.SingleConnectionDataSource(conexion, true));

            assertThat(ContextoDeTienda.deLaTienda(una, () -> unaSolaConexion.queryForList(
                    "select distinct empresa_id from usuarios", Long.class))).containsExactly(una);
            assertThat(ContextoDeTienda.deLaTienda(otra, () -> unaSolaConexion.queryForList(
                    "select distinct empresa_id from usuarios", Long.class))).containsExactly(otra);
            assertThat(unaSolaConexion.queryForObject("select count(*) from usuarios", Integer.class)).isZero();
        }
    }

    private Long tienda(String nombre) {
        int numero = ++tiendas;
        String correo = nombre.toLowerCase() + numero + "@filas.test";
        Long empresaId = empresaService.registrar(nombre + " por filas " + numero, "9071" + numero + "000-1", correo,
                "Admin", correo, CLAVE).id();
        Long adminId = jdbc.queryForObject("select id from usuarios where email = ?", Long.class, correo);
        procesoService.crear(empresaId, adminId, "Proceso de " + nombre, "Lo que hace", "Ops");
        if ("Una".equals(nombre)) {
            procesoService.crear(empresaId, adminId, "Otro de " + nombre, "Lo que hace", "Ops");
        }
        return empresaId;
    }
}
