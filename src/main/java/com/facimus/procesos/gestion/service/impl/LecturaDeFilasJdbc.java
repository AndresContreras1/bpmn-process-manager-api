package com.facimus.procesos.gestion.service.impl;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Component;

import com.facimus.procesos.gestion.service.LecturaDeFilas;

/** PR 66: las filas de la exportacion con JdbcTemplate, cada una como mapa en el orden de sus columnas. */
@Component
public class LecturaDeFilasJdbc implements LecturaDeFilas {

    private final JdbcTemplate jdbc;

    public LecturaDeFilasJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void recorrer(String consulta, Long empresaId, String cursor, Consumer<Map<String, Object>> cadaFila) {
        long[] ultimo = {0};
        int[] leidas = new int[1];
        do {
            leidas[0] = 0;
            jdbc.query(consulta, (RowCallbackHandler) fila -> {
                Map<String, Object> mapa = comoMapa(fila);
                ultimo[0] = ((Number) mapa.get(cursor)).longValue();
                leidas[0]++;
                cadaFila.accept(mapa);
            }, empresaId, ultimo[0], POR_VUELTA);
        } while (leidas[0] == POR_VUELTA);
    }

    @Override
    public List<Map<String, Object>> filas(String consulta, Object... parametros) {
        return jdbc.query(consulta, (fila, numero) -> comoMapa(fila), parametros);
    }

    /** La fila actual: cada columna por su nombre en camelCase, en el orden de la consulta. */
    static Map<String, Object> comoMapa(ResultSet fila) throws SQLException {
        ResultSetMetaData columnas = fila.getMetaData();
        Map<String, Object> mapa = new LinkedHashMap<>();
        for (int i = 1; i <= columnas.getColumnCount(); i++) {
            mapa.put(camello(columnas.getColumnLabel(i)), valor(fila, i, columnas.getColumnType(i)));
        }
        return mapa;
    }

    /** {@code fecha_de_creacion} como {@code fechaDeCreacion}. */
    static String camello(String columna) {
        StringBuilder nombre = new StringBuilder();
        boolean mayuscula = false;
        for (char letra : columna.toCharArray()) {
            if (letra == '_') {
                mayuscula = !nombre.isEmpty();
            } else {
                nombre.append(mayuscula ? Character.toUpperCase(letra) : letra);
                mayuscula = false;
            }
        }
        return nombre.toString();
    }

    private static Object valor(ResultSet fila, int columna, int tipo) throws SQLException {
        return switch (tipo) {
            case Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE -> texto(fila.getObject(columna, LocalDateTime.class));
            case Types.DATE -> texto(fila.getObject(columna, LocalDate.class));
            case Types.ARRAY -> lista(fila.getArray(columna));
            default -> fila.getObject(columna);
        };
    }

    /** Como lo escribe Java: es tambien como entra el momento de un registro en el hash de la bitacora. */
    private static String texto(Object fecha) {
        return fecha == null ? null : fecha.toString();
    }

    private static Object lista(Array arreglo) throws SQLException {
        return arreglo == null ? null : Arrays.asList((Object[]) arreglo.getArray());
    }
}
