package com.facimus.procesos.gestion.service.impl;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeMap;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.common.Huella;
import com.facimus.procesos.common.auditoria.SucesoAuditable;
import com.facimus.procesos.gestion.dto.response.PaginaDeBitacoraResponse;
import com.facimus.procesos.gestion.dto.response.RegistroDeBitacoraResponse;
import com.facimus.procesos.gestion.dto.response.VerificacionDeBitacoraResponse;
import com.facimus.procesos.gestion.service.BitacoraService;

import tools.jackson.databind.json.JsonMapper;

/**
 * PR 31: la bitacora con SQL a mano, porque lo que importa es el orden exacto de los campos que entran en el hash.
 * Agregar toma un candado de PostgreSQL por tienda ({@code pg_advisory_xact_lock}) hasta el commit: dos sucesos de la
 * misma tienda a la vez no se llevan la misma secuencia ni el mismo hash anterior, y los de tiendas distintas no se
 * esperan entre si.
 */
@Service
@Transactional(readOnly = true)
public class BitacoraServiceImpl implements BitacoraService {

    /** El hash anterior del primer registro de cada tienda. */
    static final String GENESIS = "0".repeat(64);

    /** El primer numero del candado: el de la bitacora, para no chocar con otro que use la API. */
    private static final int CANDADO_DE_LA_BITACORA = 31;

    private static final String COLUMNAS = "secuencia, momento, actor_tipo, actor_id, accion, recurso, recurso_id, "
            + "exito, ip, user_agent, request_id, trace_id, detalle, hash_anterior, hash";

    private final JdbcTemplate jdbc;
    private final JsonMapper json;

    public BitacoraServiceImpl(JdbcTemplate jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    @Transactional
    public void anotar(SucesoAuditable suceso) {
        Long empresaId = suceso.empresaId();
        jdbc.query("select pg_advisory_xact_lock(?, ?)", (RowCallbackHandler) fila -> {
        }, CANDADO_DE_LA_BITACORA, empresaId.intValue());
        List<Object[]> ultimo = jdbc.query("select secuencia, hash from bitacora where empresa_id = ? "
                + "order by secuencia desc limit 1", (fila, n) -> new Object[] {fila.getLong(1), fila.getString(2)},
                empresaId);
        long secuencia = ultimo.isEmpty() ? 1 : (Long) ultimo.get(0)[0] + 1;
        String anterior = ultimo.isEmpty() ? GENESIS : (String) ultimo.get(0)[1];
        String detalle = json.writeValueAsString(new TreeMap<>(suceso.detalle()));
        Object[] campos = {empresaId, secuencia, suceso.momento(), suceso.actorTipo().name(), suceso.actorId(),
            suceso.accion(), suceso.recurso(), suceso.recursoId(), suceso.exito(), suceso.ip(), suceso.userAgent(),
            suceso.requestId(), suceso.traceId(), detalle, anterior};
        Object[] fila = Arrays.copyOf(campos, campos.length + 1);
        fila[campos.length] = hash(campos);
        jdbc.update("insert into bitacora (empresa_id, " + COLUMNAS + ") "
                + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", fila);
    }

    @Override
    public PaginaDeBitacoraResponse buscar(Long empresaId, Filtro filtro, Long antes, int tamano) {
        List<Object> parametros = new ArrayList<>();
        StringBuilder sql = new StringBuilder(donde(empresaId, filtro, parametros));
        if (antes != null) {
            sql.append(" and secuencia < ?");
            parametros.add(antes);
        }
        sql.append(" order by secuencia desc limit ?");
        parametros.add(tamano + 1);
        List<RegistroDeBitacoraResponse> registros = jdbc.query(sql.toString(), (fila, n) -> registro(fila),
                parametros.toArray());
        if (registros.size() <= tamano) {
            return new PaginaDeBitacoraResponse(registros, null);
        }
        List<RegistroDeBitacoraResponse> pagina = registros.subList(0, tamano);
        return new PaginaDeBitacoraResponse(List.copyOf(pagina), pagina.get(tamano - 1).secuencia());
    }

    @Override
    public List<RegistroDeBitacoraResponse> exportar(Long empresaId, Filtro filtro, int tope) {
        List<Object> parametros = new ArrayList<>();
        String sql = donde(empresaId, filtro, parametros) + " order by secuencia asc limit ?";
        parametros.add(tope);
        return jdbc.query(sql, (fila, n) -> registro(fila), parametros.toArray());
    }

    @Override
    public VerificacionDeBitacoraResponse verificar(Long empresaId) {
        long[] revisados = {0};
        String[] anterior = {GENESIS};
        VerificacionDeBitacoraResponse[] rotura = {null};
        jdbc.query("select " + COLUMNAS + " from bitacora where empresa_id = ? order by secuencia asc",
                (RowCallbackHandler) fila -> {
                    if (rotura[0] != null) {
                        return;
                    }
                    long secuencia = fila.getLong("secuencia");
                    revisados[0]++;
                    if (secuencia != revisados[0]) {
                        rotura[0] = rota(revisados[0], secuencia, "Falta el registro " + revisados[0]
                                + ": la secuencia salta a " + secuencia + ".");
                    } else if (!anterior[0].equals(fila.getString("hash_anterior"))) {
                        rotura[0] = rota(revisados[0], secuencia, "No sigue al registro anterior.");
                    } else if (!hash(campos(empresaId, fila)).equals(fila.getString("hash"))) {
                        rotura[0] = rota(revisados[0], secuencia, "El hash no coincide con lo que dice el registro.");
                    }
                    anterior[0] = fila.getString("hash");
                }, empresaId);
        return rotura[0] != null ? rotura[0] : new VerificacionDeBitacoraResponse(true, revisados[0], null, null);
    }

    private static VerificacionDeBitacoraResponse rota(long revisados, long secuencia, String motivo) {
        return new VerificacionDeBitacoraResponse(false, revisados, secuencia, motivo);
    }

    private static String donde(Long empresaId, Filtro filtro, List<Object> parametros) {
        StringBuilder sql = new StringBuilder("select " + COLUMNAS + " from bitacora where empresa_id = ?");
        parametros.add(empresaId);
        if (filtro.accion() != null) {
            sql.append(" and accion = ?");
            parametros.add(filtro.accion());
        }
        if (filtro.actorId() != null) {
            sql.append(" and actor_id = ?");
            parametros.add(filtro.actorId());
        }
        if (filtro.desde() != null) {
            sql.append(" and momento >= ?");
            parametros.add(filtro.desde());
        }
        if (filtro.hasta() != null) {
            sql.append(" and momento < ?");
            parametros.add(filtro.hasta());
        }
        return sql.toString();
    }

    /** Los campos como entraron en el hash, en el mismo orden, leidos de vuelta de la fila. */
    private static Object[] campos(Long empresaId, ResultSet fila) throws SQLException {
        return new Object[] {empresaId, fila.getLong("secuencia"), fila.getObject("momento", LocalDateTime.class),
            fila.getString("actor_tipo"), fila.getObject("actor_id", Long.class), fila.getString("accion"),
            fila.getString("recurso"), fila.getObject("recurso_id", Long.class), fila.getBoolean("exito"),
            fila.getString("ip"), fila.getString("user_agent"), fila.getString("request_id"),
            fila.getString("trace_id"), fila.getString("detalle"), fila.getString("hash_anterior")};
    }

    /**
     * El SHA-256 de los campos canonicos y del hash anterior: un arreglo JSON, que no confunde un campo vacio con otro
     * que no lo es ni deja que un separador dentro de un campo se haga pasar por otro campo.
     */
    private String hash(Object[] campos) {
        Object[] canonicos = Arrays.stream(campos)
                .map(campo -> campo instanceof LocalDateTime momento ? momento.toString() : campo)
                .toArray();
        return Huella.de(json.writeValueAsString(canonicos));
    }

    private static RegistroDeBitacoraResponse registro(ResultSet fila) throws SQLException {
        return new RegistroDeBitacoraResponse(fila.getLong("secuencia"), fila.getObject("momento", LocalDateTime.class),
                fila.getString("actor_tipo"), fila.getObject("actor_id", Long.class), fila.getString("accion"),
                fila.getString("recurso"), fila.getObject("recurso_id", Long.class), fila.getBoolean("exito"),
                fila.getString("ip"), fila.getString("user_agent"), fila.getString("request_id"),
                fila.getString("trace_id"), fila.getString("detalle"), fila.getString("hash"));
    }
}
