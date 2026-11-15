package com.facimus.procesos.gestion.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;

import com.facimus.procesos.gestion.service.LecturaDeFilas;

/** PR 66: las filas de la exportacion como mapas, y el recorrido de una tabla grande de a pedazos. */
@ExtendWith(MockitoExtension.class)
class LecturaDeFilasJdbcTest {

    private static final String CONSULTA = "select id from casos where empresa_id = ? and id > ? order by id limit ?";

    @Mock
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("cada columna sale con su nombre en camelCase y su valor: fechas en ISO, arreglos como listas")
    void comoMapa() throws SQLException {
        ResultSet fila = mock(ResultSet.class);
        ResultSetMetaData columnas = mock(ResultSetMetaData.class);
        when(fila.getMetaData()).thenReturn(columnas);
        when(columnas.getColumnCount()).thenReturn(7);
        columna(columnas, 1, "id", Types.BIGINT);
        columna(columnas, 2, "fecha_de_creacion", Types.TIMESTAMP);
        columna(columnas, 3, "proxima_revision", Types.DATE);
        columna(columnas, 4, "etiquetas", Types.ARRAY);
        columna(columnas, 5, "fecha_fin", Types.TIMESTAMP_WITH_TIMEZONE);
        columna(columnas, 6, "menciones", Types.ARRAY);
        columna(columnas, 7, "_activo", Types.BOOLEAN);
        when(fila.getObject(1)).thenReturn(7L);
        when(fila.getObject(2, LocalDateTime.class)).thenReturn(LocalDateTime.of(2026, 11, 12, 10, 0, 1));
        when(fila.getObject(3, LocalDate.class)).thenReturn(LocalDate.of(2027, 1, 31));
        Array etiquetas = mock(Array.class);
        when(etiquetas.getArray()).thenReturn(new String[] {"iso", null});
        when(fila.getArray(4)).thenReturn(etiquetas);
        when(fila.getObject(5, LocalDateTime.class)).thenReturn(null);
        when(fila.getArray(6)).thenReturn(null);
        when(fila.getObject(7)).thenReturn(true);

        Map<String, Object> mapa = LecturaDeFilasJdbc.comoMapa(fila);

        assertThat(mapa.keySet()).containsExactly("id", "fechaDeCreacion", "proximaRevision", "etiquetas", "fechaFin",
                "menciones", "activo");
        assertThat(mapa).containsEntry("id", 7L).containsEntry("fechaDeCreacion", "2026-11-12T10:00:01")
                .containsEntry("proximaRevision", "2027-01-31").containsEntry("etiquetas", Arrays.asList("iso", null))
                .containsEntry("fechaFin", null).containsEntry("menciones", null).containsEntry("activo", true);
    }

    @Test
    @DisplayName("camelCase: el guion bajo pone en mayuscula la letra siguiente, salvo al principio")
    void camello() {
        assertThat(LecturaDeFilasJdbc.camello("fecha_de_creacion")).isEqualTo("fechaDeCreacion");
        assertThat(LecturaDeFilasJdbc.camello("id")).isEqualTo("id");
        assertThat(LecturaDeFilasJdbc.camello("_id")).isEqualTo("id");
        assertThat(LecturaDeFilasJdbc.camello("hash__anterior")).isEqualTo("hashAnterior");
        assertThat(LecturaDeFilasJdbc.camello("version_del_proceso")).isEqualTo("versionDelProceso");
    }

    @Test
    @DisplayName("recorrer pide de a 500 filas desde el ultimo id visto, y para cuando una vuelta trae menos")
    void recorrerDeAPedazos() {
        List<Long> desde = new ArrayList<>();
        doAnswer(llamada -> {
            long ultimo = llamada.getArgument(3);
            desde.add(ultimo);
            RowCallbackHandler cadaFila = llamada.getArgument(1);
            int cuantas = ultimo == 0 ? LecturaDeFilas.POR_VUELTA : 2;
            for (long id = ultimo + 1; id <= ultimo + cuantas; id++) {
                cadaFila.processRow(filaCon("id", id));
            }
            return null;
        }).when(jdbc).query(eq(CONSULTA), any(RowCallbackHandler.class), eq(3L), any(), eq(LecturaDeFilas.POR_VUELTA));
        List<Object> ids = new ArrayList<>();

        new LecturaDeFilasJdbc(jdbc).recorrer(CONSULTA, 3L, fila -> ids.add(fila.get("id")));

        assertThat(desde).containsExactly(0L, 500L);
        assertThat(ids).hasSize(502).startsWith(1L).endsWith(502L);
    }

    @Test
    @DisplayName("recorrer por otra columna que crece avanza por ella; una vuelta vacia termina")
    void recorrerPorLaSecuencia() {
        String consulta = "select secuencia from bitacora where empresa_id = ? and secuencia > ? order by secuencia "
                + "limit ?";
        doAnswer(llamada -> {
            long ultimo = llamada.getArgument(3);
            RowCallbackHandler cadaFila = llamada.getArgument(1);
            if (ultimo == 0) {
                for (long secuencia = 1; secuencia <= LecturaDeFilas.POR_VUELTA; secuencia++) {
                    cadaFila.processRow(filaCon("secuencia", secuencia));
                }
            }
            return null;
        }).when(jdbc).query(eq(consulta), any(RowCallbackHandler.class), eq(3L), any(), eq(LecturaDeFilas.POR_VUELTA));
        List<Object> secuencias = new ArrayList<>();

        new LecturaDeFilasJdbc(jdbc).recorrer(consulta, 3L, "secuencia", fila -> secuencias.add(fila.get("secuencia")));

        assertThat(secuencias).hasSize(500);
        verify(jdbc, times(2)).query(eq(consulta), any(RowCallbackHandler.class), eq(3L), any(),
                eq(LecturaDeFilas.POR_VUELTA));
        verify(jdbc).query(eq(consulta), any(RowCallbackHandler.class), eq(3L), eq(500L),
                eq(LecturaDeFilas.POR_VUELTA));
    }

    @Test
    @DisplayName("las filas de una consulta chica salen como mapas, con sus parametros")
    @SuppressWarnings("unchecked")
    void filas() {
        when(jdbc.query(eq("select numero from versiones_proceso where empresa_id = ? and proceso_id = ?"),
                any(RowMapper.class), eq(3L), eq(9L))).thenAnswer(llamada -> {
                    RowMapper<Map<String, Object>> mapeo = llamada.getArgument(1);
                    return List.of(mapeo.mapRow(filaCon("numero", 2L), 0));
                });

        assertThat(new LecturaDeFilasJdbc(jdbc).filas(
                "select numero from versiones_proceso where empresa_id = ? and proceso_id = ?", 3L, 9L))
                .containsExactly(Map.of("numero", 2L));
    }

    private static void columna(ResultSetMetaData columnas, int numero, String nombre, int tipo)
            throws SQLException {
        when(columnas.getColumnLabel(numero)).thenReturn(nombre);
        when(columnas.getColumnType(numero)).thenReturn(tipo);
    }

    private static ResultSet filaCon(String columna, long valor) throws SQLException {
        ResultSet fila = mock(ResultSet.class);
        ResultSetMetaData columnas = mock(ResultSetMetaData.class);
        when(fila.getMetaData()).thenReturn(columnas);
        when(columnas.getColumnCount()).thenReturn(1);
        columna(columnas, 1, columna, Types.BIGINT);
        when(fila.getObject(1)).thenReturn(valor);
        return fila;
    }
}
