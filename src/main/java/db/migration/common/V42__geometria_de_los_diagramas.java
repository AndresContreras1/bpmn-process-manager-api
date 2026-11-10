package db.migration.common;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import com.facimus.procesos.modelado.model.AristaBpmn;
import com.facimus.procesos.modelado.model.DisposicionBpmn;
import com.facimus.procesos.modelado.model.DisposicionBpmn.ArcoPlano;
import com.facimus.procesos.modelado.model.DisposicionBpmn.Arista;
import com.facimus.procesos.modelado.model.DisposicionBpmn.Forma;
import com.facimus.procesos.modelado.model.DisposicionBpmn.Geometria;
import com.facimus.procesos.modelado.model.DisposicionBpmn.LanePlana;
import com.facimus.procesos.modelado.model.DisposicionBpmn.Limites;
import com.facimus.procesos.modelado.model.DisposicionBpmn.MensajePlano;
import com.facimus.procesos.modelado.model.DisposicionBpmn.NodoPlano;
import com.facimus.procesos.modelado.model.DisposicionBpmn.PoolPlano;

/**
 * PR 52: la geometria BPMN DI de los diagramas que ya existian. Las posiciones guardadas son relativas a la lane; esta
 * migracion las convierte en coordenadas absolutas con la misma cuenta que dibuja la web ({@code lienzo.ts}), que es
 * {@link DisposicionBpmn}. Solo cuenta lo activo: lo dado de baja no se dibuja.
 *
 * <p>Va en Java porque la cuenta no cabe en SQL. Usa la clase del dominio: si la cuenta cambia algun dia, esta
 * migracion ya habra corrido en cada base que tenia diagramas, y en una base nueva no hay nada que convertir.
 */
public class V42__geometria_de_los_diagramas extends BaseJavaMigration {

    @Override
    public void migrate(Context contexto) throws SQLException {
        Connection conexion = contexto.getConnection();
        try (Statement sentencia = conexion.createStatement()) {
            // Las filas de todas las tiendas: la migracion corre como el sistema (PR 51)
            sentencia.execute("select set_config('app.sistema', 'on', true)");
        }
        for (long[] proceso : procesos(conexion)) {
            convertir(conexion, proceso[0], proceso[1]);
        }
    }

    /** Pares de empresa y proceso con algun pool activo. */
    private static List<long[]> procesos(Connection conexion) throws SQLException {
        List<long[]> procesos = new ArrayList<>();
        try (Statement sentencia = conexion.createStatement();
                ResultSet filas = sentencia.executeQuery(
                        "select distinct empresa_id, proceso_id from pools where activo order by proceso_id")) {
            while (filas.next()) {
                procesos.add(new long[] {filas.getLong(1), filas.getLong(2)});
            }
        }
        return procesos;
    }

    private static void convertir(Connection conexion, long empresaId, long procesoId) throws SQLException {
        List<PoolPlano> pools = new ArrayList<>();
        try (PreparedStatement consulta = conexion.prepareStatement("""
                select id, bpmn_id, tipo_participante, orden, caja_negra from pools
                where proceso_id = ? and activo
                """)) {
            consulta.setLong(1, procesoId);
            try (ResultSet filas = consulta.executeQuery()) {
                while (filas.next()) {
                    pools.add(new PoolPlano(filas.getLong(1), filas.getString(2), filas.getString(3), filas.getInt(4),
                            filas.getBoolean(5)));
                }
            }
        }
        List<LanePlana> lanes = new ArrayList<>();
        try (PreparedStatement consulta = conexion.prepareStatement("""
                select l.id, l.bpmn_id, l.pool_id, l.orden from lanes l join pools p on p.id = l.pool_id
                where p.proceso_id = ? and l.activo and p.activo
                """)) {
            consulta.setLong(1, procesoId);
            try (ResultSet filas = consulta.executeQuery()) {
                while (filas.next()) {
                    lanes.add(new LanePlana(filas.getLong(1), filas.getString(2), filas.getLong(3), filas.getInt(4)));
                }
            }
        }
        List<NodoPlano> nodos = new ArrayList<>();
        try (PreparedStatement consulta = conexion.prepareStatement("""
                select n.id, n.bpmn_id, n.lane_id, n.tipo_nodo, n.posicion_x, n.posicion_y, n.nombre
                from nodos_flujo n join lanes l on l.id = n.lane_id join pools p on p.id = l.pool_id
                where p.proceso_id = ? and n.activo and l.activo and p.activo
                """)) {
            consulta.setLong(1, procesoId);
            try (ResultSet filas = consulta.executeQuery()) {
                while (filas.next()) {
                    nodos.add(new NodoPlano(filas.getLong(1), filas.getString(2), filas.getLong(3),
                            clase(filas.getString(4)), filas.getInt(5), filas.getInt(6), filas.getString(7)));
                }
            }
        }
        List<ArcoPlano> arcos = new ArrayList<>();
        try (PreparedStatement consulta = conexion.prepareStatement("""
                select a.bpmn_id, a.origen_id, a.destino_id, a.etiqueta from arcos a join pools p on p.id = a.pool_id
                where p.proceso_id = ? and a.activo and p.activo
                """)) {
            consulta.setLong(1, procesoId);
            try (ResultSet filas = consulta.executeQuery()) {
                while (filas.next()) {
                    arcos.add(new ArcoPlano(filas.getString(1), filas.getLong(2), filas.getLong(3),
                            filas.getString(4)));
                }
            }
        }
        List<MensajePlano> mensajes = new ArrayList<>();
        try (PreparedStatement consulta = conexion.prepareStatement("""
                select id, bpmn_id, pool_origen_id, pool_destino_id, nodo_origen_id, nodo_destino_id from mensajes
                where proceso_id = ? and activo
                """)) {
            consulta.setLong(1, procesoId);
            try (ResultSet filas = consulta.executeQuery()) {
                while (filas.next()) {
                    long nodoOrigen = filas.getLong(5);
                    Long origen = filas.wasNull() ? null : nodoOrigen;
                    long nodoDestino = filas.getLong(6);
                    Long destino = filas.wasNull() ? null : nodoDestino;
                    mensajes.add(new MensajePlano(filas.getLong(1), filas.getString(2), filas.getLong(3),
                            filas.getLong(4), origen, destino));
                }
            }
        }
        guardar(conexion, empresaId, procesoId, DisposicionBpmn.calcular(pools, lanes, nodos, arcos, mensajes));
    }

    private static void guardar(Connection conexion, long empresaId, long procesoId, Geometria geometria)
            throws SQLException {
        try (PreparedStatement insercion = conexion.prepareStatement("""
                insert into formas_bpmn (empresa_id, proceso_id, elemento, tipo, x, y, ancho, alto,
                    etiqueta_x, etiqueta_y, etiqueta_ancho, etiqueta_alto)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            for (Forma forma : geometria.formas()) {
                insercion.setLong(1, empresaId);
                insercion.setLong(2, procesoId);
                insercion.setString(3, forma.elemento());
                insercion.setString(4, forma.tipo());
                insercion.setDouble(5, forma.limites().x());
                insercion.setDouble(6, forma.limites().y());
                insercion.setDouble(7, forma.limites().ancho());
                insercion.setDouble(8, forma.limites().alto());
                etiqueta(insercion, 9, forma.etiqueta());
                insercion.addBatch();
            }
            insercion.executeBatch();
        }
        try (PreparedStatement insercion = conexion.prepareStatement("""
                insert into aristas_bpmn (empresa_id, proceso_id, elemento, tipo, puntos,
                    etiqueta_x, etiqueta_y, etiqueta_ancho, etiqueta_alto)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            for (Arista arista : geometria.aristas()) {
                insercion.setLong(1, empresaId);
                insercion.setLong(2, procesoId);
                insercion.setString(3, arista.elemento());
                insercion.setString(4, arista.tipo());
                insercion.setString(5, AristaBpmn.escribir(arista.puntos()));
                etiqueta(insercion, 6, arista.etiqueta());
                insercion.addBatch();
            }
            insercion.executeBatch();
        }
    }

    private static void etiqueta(PreparedStatement insercion, int desde, Limites etiqueta) throws SQLException {
        if (etiqueta == null) {
            for (int i = 0; i < 4; i++) {
                insercion.setNull(desde + i, Types.DOUBLE);
            }
            return;
        }
        insercion.setDouble(desde, etiqueta.x());
        insercion.setDouble(desde + 1, etiqueta.y());
        insercion.setDouble(desde + 2, etiqueta.ancho());
        insercion.setDouble(desde + 3, etiqueta.alto());
    }

    private static String clase(String tipoNodo) {
        return switch (tipoNodo) {
            case "GATEWAY" -> "COMPUERTA";
            case "EVENTO" -> "EVENTO";
            default -> "TAREA";
        };
    }
}
