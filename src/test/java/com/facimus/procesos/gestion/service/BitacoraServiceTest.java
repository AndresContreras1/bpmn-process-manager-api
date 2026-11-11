package com.facimus.procesos.gestion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;

import com.facimus.procesos.common.auditoria.SucesoAuditable;
import com.facimus.procesos.common.auditoria.SucesoAuditable.TipoDeActor;
import com.facimus.procesos.common.security.ContextoDeTienda;
import com.facimus.procesos.gestion.dto.response.PaginaDeBitacoraResponse;
import com.facimus.procesos.gestion.dto.response.RegistroDeBitacoraResponse;
import com.facimus.procesos.gestion.dto.response.VerificacionDeBitacoraResponse;
import com.facimus.procesos.gestion.service.impl.BitacoraServiceImpl;

import tools.jackson.databind.json.JsonMapper;

/**
 * PR 51: la bitacora encadenada sin PostgreSQL. La tabla es una lista en memoria detras de un JdbcTemplate de mentira,
 * asi que se ve que escribe cada registro, como lo encadena con el anterior y que rotura encuentra la verificacion. El
 * SQL de verdad lo prueba BitacoraIntegracionTest.
 */
class BitacoraServiceTest {

    private static final Long EMPRESA = 7L;
    private static final String GENESIS = "0".repeat(64);
    private static final List<String> COLUMNAS = List.of("empresa_id", "secuencia", "momento", "actor_tipo",
            "actor_id", "accion", "recurso", "recurso_id", "exito", "ip", "user_agent", "request_id", "trace_id",
            "detalle", "hash_anterior", "hash");

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final List<Map<String, Object>> tabla = new ArrayList<>();
    private final List<Object> candados = new ArrayList<>();
    private final List<Object[]> parametrosDeConsulta = new ArrayList<>();
    private BitacoraServiceImpl bitacora;

    @BeforeEach
    void laTablaEnMemoria() throws SQLException {
        bitacora = new BitacoraServiceImpl(jdbc, JsonMapper.builder().build());
        doAnswer(invocacion -> {
            Object[] fila = valores(invocacion.getArguments());
            Map<String, Object> registro = new HashMap<>();
            for (int i = 0; i < COLUMNAS.size(); i++) {
                registro.put(COLUMNAS.get(i), fila[i]);
            }
            tabla.add(registro);
            return 1;
        }).when(jdbc).update(anyString(), any(Object[].class));
        doAnswer(invocacion -> {
            String sql = invocacion.getArgument(0);
            RowCallbackHandler manejador = invocacion.getArgument(1);
            if (sql.contains("pg_advisory_xact_lock")) {
                candados.add(List.of(valores(invocacion.getArguments())));
                candados.add(ContextoDeTienda.actual().orElseThrow());
                return null;
            }
            for (Map<String, Object> registro : tabla) {
                manejador.processRow(fila(registro));
            }
            return null;
        }).when(jdbc).query(anyString(), any(RowCallbackHandler.class), any(Object[].class));
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenAnswer(invocacion -> {
            String sql = invocacion.getArgument(0);
            RowMapper<?> mapeador = invocacion.getArgument(1);
            Object[] parametros = valores(invocacion.getArguments());
            parametrosDeConsulta.add(parametros);
            List<Map<String, Object>> filas = new ArrayList<>(tabla);
            if (sql.contains("limit 1")) {
                filas = tabla.isEmpty() ? List.of() : List.of(tabla.getLast());
            } else {
                if (sql.contains("desc")) {
                    filas = filas.reversed();
                }
                filas = filas.subList(0, Math.min(filas.size(), (Integer) parametros[parametros.length - 1]));
            }
            List<Object> leidos = new ArrayList<>();
            for (int i = 0; i < filas.size(); i++) {
                leidos.add(mapeador.mapRow(fila(filas.get(i)), i));
            }
            return leidos;
        });
    }

    @Test
    @DisplayName("Cada registro sigue al anterior: secuencia, hash anterior, detalle ordenado y candado de la tienda")
    void anotarEncadena() {
        bitacora.anotar(suceso("LOGIN", Map.of("b", 2, "a", 1)));
        bitacora.anotar(suceso("LOGOUT", Map.of()));

        assertThat(tabla).hasSize(2);
        Map<String, Object> primero = tabla.get(0);
        Map<String, Object> segundo = tabla.get(1);
        assertThat(primero.get("secuencia")).isEqualTo(1L);
        assertThat(primero.get("hash_anterior")).isEqualTo(GENESIS);
        assertThat(primero.get("detalle")).isEqualTo("{\"a\":1,\"b\":2}");
        assertThat(primero.get("actor_tipo")).isEqualTo("USUARIO");
        assertThat(primero.get("empresa_id")).isEqualTo(EMPRESA);
        assertThat((String) primero.get("hash")).hasSize(64).isNotEqualTo(GENESIS);
        assertThat(segundo.get("secuencia")).isEqualTo(2L);
        assertThat(segundo.get("hash_anterior")).isEqualTo(primero.get("hash"));
        assertThat(segundo.get("hash")).isNotEqualTo(primero.get("hash"));
        assertThat(candados.get(0)).isEqualTo(List.of(31, 7));
        assertThat(candados.get(1)).isEqualTo(ContextoDeTienda.Alcance.deLaTienda(EMPRESA));
    }

    @Test
    @DisplayName("Una cadena sin tocar se verifica entera; una vacia tambien")
    void verificarIntegra() {
        assertThat(bitacora.verificar(EMPRESA)).isEqualTo(new VerificacionDeBitacoraResponse(true, 0, null, null));

        anotarTres();

        assertThat(bitacora.verificar(EMPRESA)).isEqualTo(new VerificacionDeBitacoraResponse(true, 3, null, null));
    }

    @Test
    @DisplayName("Un registro cambiado despues de escrito ya no coincide con su hash")
    void verificarAlterado() {
        anotarTres();
        tabla.get(1).put("detalle", "{\"a\":2}");

        assertThat(bitacora.verificar(EMPRESA)).isEqualTo(new VerificacionDeBitacoraResponse(false, 2, 2L,
                "El hash no coincide con lo que dice el registro."));
    }

    @Test
    @DisplayName("Un registro borrado se nota porque la secuencia salta")
    void verificarBorrado() {
        anotarTres();
        tabla.remove(1);

        assertThat(bitacora.verificar(EMPRESA)).isEqualTo(new VerificacionDeBitacoraResponse(false, 2, 3L,
                "Falta el registro 2: la secuencia salta a 3."));
    }

    @Test
    @DisplayName("Un registro que no apunta al hash del anterior rompe la cadena ahi, y lo que sigue no se revisa")
    void verificarSinEncadenar() {
        anotarTres();
        tabla.get(1).put("hash_anterior", GENESIS);
        tabla.get(2).put("detalle", "{}");

        assertThat(bitacora.verificar(EMPRESA)).isEqualTo(new VerificacionDeBitacoraResponse(false, 2, 2L,
                "No sigue al registro anterior."));
    }

    @Test
    @DisplayName("Buscar pagina de lo mas nuevo a lo mas viejo y da el cursor de la pagina siguiente")
    void buscarPagina() {
        anotarTres();
        BitacoraService.Filtro filtro = new BitacoraService.Filtro("LOGIN", 5L, null, null);

        PaginaDeBitacoraResponse primera = bitacora.buscar(EMPRESA, filtro, null, 2);
        PaginaDeBitacoraResponse completa = bitacora.buscar(EMPRESA, filtro, null, 3);

        assertThat(primera.registros()).extracting(RegistroDeBitacoraResponse::secuencia).containsExactly(3L, 2L);
        assertThat(primera.siguiente()).isEqualTo(2L);
        assertThat(completa.registros()).extracting(RegistroDeBitacoraResponse::secuencia)
                .containsExactly(3L, 2L, 1L);
        assertThat(completa.siguiente()).isNull();
        assertThat(completa.registros().getFirst().accion()).isEqualTo("EVENTO_3");
        assertThat(completa.registros().getFirst().hash()).isEqualTo(tabla.get(2).get("hash"));
        assertThat(parametrosDeConsulta.get(parametrosDeConsulta.size() - 2))
                .containsExactly(EMPRESA, "LOGIN", "LOGIN", 5L, 5L, null, null, null, null, null, null, 3);
    }

    @Test
    @DisplayName("Exportar lee de lo mas viejo a lo mas nuevo, hasta el tope y sin cursor")
    void exportar() {
        anotarTres();
        LocalDateTime desde = LocalDateTime.of(2026, 11, 1, 0, 0);

        List<RegistroDeBitacoraResponse> exportados = bitacora.exportar(EMPRESA,
                new BitacoraService.Filtro(null, null, desde, null), 2);

        assertThat(exportados).extracting(RegistroDeBitacoraResponse::secuencia).containsExactly(1L, 2L);
        assertThat(parametrosDeConsulta.getLast())
                .containsExactly(EMPRESA, null, null, null, null, desde, desde, null, null, null, null, 2);
    }

    private void anotarTres() {
        for (int i = 1; i <= 3; i++) {
            bitacora.anotar(suceso("EVENTO_" + i, Map.of("n", i)));
        }
    }

    private static SucesoAuditable suceso(String accion, Map<String, Object> detalle) {
        return new SucesoAuditable(EMPRESA, TipoDeActor.USUARIO, 5L, accion, "USUARIO", 5L, true, detalle,
                "10.0.0.1", "JUnit", "req-1", "trace-1", LocalDateTime.of(2026, 11, 11, 12, 0));
    }

    /** Los parametros variables de una llamada, esten expandidos o en su arreglo. */
    private static Object[] valores(Object[] argumentos) {
        Object ultimo = argumentos[argumentos.length - 1];
        if ((argumentos.length == 2 || argumentos.length == 3) && ultimo instanceof Object[] arreglo) {
            return arreglo;
        }
        int desde = argumentos[1] instanceof RowCallbackHandler || argumentos[1] instanceof RowMapper ? 2 : 1;
        return Arrays.copyOfRange(argumentos, desde, argumentos.length);
    }

    private static ResultSet fila(Map<String, Object> registro) throws SQLException {
        ResultSet fila = mock(ResultSet.class);
        when(fila.getLong(anyString())).thenAnswer(i -> registro.get(i.<String>getArgument(0)));
        when(fila.getLong(1)).thenAnswer(i -> registro.get("secuencia"));
        when(fila.getString(2)).thenAnswer(i -> registro.get("hash"));
        when(fila.getString(anyString())).thenAnswer(i -> registro.get(i.<String>getArgument(0)));
        when(fila.getBoolean(anyString())).thenAnswer(i -> registro.get(i.<String>getArgument(0)));
        when(fila.getObject(anyString(), any(Class.class))).thenAnswer(i -> registro.get(i.<String>getArgument(0)));
        return fila;
    }
}
