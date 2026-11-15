package com.facimus.procesos.gestion.service.impl;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.common.Huella;
import com.facimus.procesos.common.auditoria.SucesoAuditable;
import com.facimus.procesos.common.security.ContextoDeTienda;
import com.facimus.procesos.gestion.dto.response.PaginaDeBitacoraResponse;
import com.facimus.procesos.gestion.dto.response.RegistroDeBitacoraResponse;
import com.facimus.procesos.gestion.dto.response.VerificacionDeBitacoraResponse;
import com.facimus.procesos.gestion.service.AnclaDeBitacoraService;
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

    /** La busqueda es una sola sentencia fija: un filtro que no se pide llega nulo y no filtra. */
    private static final String DONDE = "select " + COLUMNAS + " from bitacora where empresa_id = ? "
            + "and (cast(? as varchar) is null or accion = ?) "
            + "and (cast(? as bigint) is null or actor_id = ?) "
            + "and (cast(? as timestamp) is null or momento >= ?) "
            + "and (cast(? as timestamp) is null or momento < ?) "
            + "and (cast(? as bigint) is null or secuencia < ?)";

    private static final String BUSCAR = DONDE + " order by secuencia desc limit ?";

    private static final String EXPORTAR = DONDE + " order by secuencia asc limit ?";

    private final JdbcTemplate jdbc;
    private final JsonMapper json;
    private final AnclaDeBitacoraService anclas;

    public BitacoraServiceImpl(JdbcTemplate jdbc, JsonMapper json, AnclaDeBitacoraService anclas) {
        this.jdbc = jdbc;
        this.json = json;
        this.anclas = anclas;
    }

    @Override
    @Transactional
    public void anotar(SucesoAuditable suceso) {
        // PR 51: el receptor corre fuera de la peticion; lo que anota es de la tienda del suceso y de ninguna otra.
        ContextoDeTienda.deLaTienda(suceso.empresaId(), () -> agregar(suceso));
    }

    private void agregar(SucesoAuditable suceso) {
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
        List<RegistroDeBitacoraResponse> registros = jdbc.query(BUSCAR, (fila, n) -> registro(fila),
                parametros(empresaId, filtro, antes, tamano + 1));
        if (registros.size() <= tamano) {
            return new PaginaDeBitacoraResponse(registros, null);
        }
        List<RegistroDeBitacoraResponse> pagina = registros.subList(0, tamano);
        return new PaginaDeBitacoraResponse(List.copyOf(pagina), pagina.get(tamano - 1).secuencia());
    }

    @Override
    public List<RegistroDeBitacoraResponse> exportar(Long empresaId, Filtro filtro, int tope) {
        return jdbc.query(EXPORTAR, (fila, n) -> registro(fila), parametros(empresaId, filtro, null, tope));
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
        return rotura[0] != null ? rotura[0] : contraElAncla(empresaId, revisados[0]);
    }

    /**
     * PR 61: una cadena que cierra por dentro puede estar reescrita entera. La ultima ancla, leida del almacen donde
     * nadie la puede cambiar, dice que hash tenia ese eslabon el dia que se anclo.
     */
    private VerificacionDeBitacoraResponse contraElAncla(Long empresaId, long revisados) {
        Optional<AnclaDeBitacoraService.Ancla> ultima = anclas.ultima(empresaId);
        if (ultima.isEmpty()) {
            return new VerificacionDeBitacoraResponse(true, revisados, null, null, null);
        }
        AnclaDeBitacoraService.Ancla ancla = ultima.get();
        if (ancla.hash() == null) {
            return rota(revisados, ancla.secuencia(), "El ancla del " + ancla.dia() + " ya no está en el almacén.");
        }
        List<String> enLaCadena = jdbc.queryForList("select hash from bitacora where empresa_id = ? and secuencia = ?",
                String.class, empresaId, ancla.secuencia());
        if (enLaCadena.isEmpty() || !ancla.hash().equals(enLaCadena.getFirst())) {
            return rota(revisados, ancla.secuencia(), "No coincide con el ancla del " + ancla.dia() + ".");
        }
        return new VerificacionDeBitacoraResponse(true, revisados, null, null, ancla.dia());
    }

    private static VerificacionDeBitacoraResponse rota(long revisados, long secuencia, String motivo) {
        return new VerificacionDeBitacoraResponse(false, revisados, secuencia, motivo, null);
    }

    /** Cada filtro, el cursor y el tope, en el orden de DONDE; uno nulo no filtra. */
    private static Object[] parametros(Long empresaId, Filtro filtro, Long antes, int limite) {
        return new Object[] {empresaId, filtro.accion(), filtro.accion(), filtro.actorId(), filtro.actorId(),
            filtro.desde(), filtro.desde(), filtro.hasta(), filtro.hasta(), antes, antes, limite};
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
