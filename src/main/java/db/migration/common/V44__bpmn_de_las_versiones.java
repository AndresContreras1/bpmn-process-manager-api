package db.migration.common;

import java.nio.charset.StandardCharsets;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import com.facimus.procesos.modelado.bpmn.EscritorBpmn;
import com.facimus.procesos.modelado.bpmn.GeometriaDelDiagrama;
import com.facimus.procesos.modelado.dto.response.DiagramaResponse;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * PR 53: el BPMN de las versiones que ya estaban publicadas. Las nuevas lo escriben al publicarse; a estas se les
 * escribe desde su instantanea, con la geometria calculada por la misma cuenta que guarda el borrador.
 *
 * <p>Antes, a cada instantanea se le agrega el {@code bpmnId} de cada elemento, que el diagrama no traia. Sale de las
 * tablas: la baja es logica, asi que las filas de lo que despues se borro siguen ahi con el suyo. La huella no cambia,
 * porque el bpmn_id no entra en la forma canonica, y ese es el unico cambio que recibe la instantanea.
 */
@SuppressWarnings("java:S101") // Flyway saca la version y la descripcion del nombre de la clase
public class V44__bpmn_de_las_versiones extends BaseJavaMigration {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static final String NODOS = "select id, bpmn_id from nodos_flujo where id = any(?)";

    /** Cada lista del diagrama y la consulta que da el bpmn_id de sus elementos, con o sin baja. */
    private static final Map<String, String> CONSULTAS = Map.of(
            "pools", "select id, bpmn_id from pools where id = any(?)",
            "lanes", "select id, bpmn_id from lanes where id = any(?)",
            "actividades", NODOS,
            "gateways", NODOS,
            "eventos", NODOS,
            "arcos", "select id, bpmn_id from arcos where id = any(?)",
            "mensajes", "select id, bpmn_id from mensajes where id = any(?)");

    @Override
    public void migrate(Context contexto) throws SQLException {
        Connection conexion = contexto.getConnection();
        try (Statement sentencia = conexion.createStatement()) {
            // Las filas de todas las tiendas: la migracion corre como el sistema (PR 51)
            sentencia.execute("select set_config('app.sistema', 'on', true)");
        }
        for (Long version : sinBpmn(conexion)) {
            convertir(conexion, version);
        }
        try (Statement sentencia = conexion.createStatement()) {
            sentencia.execute("alter table versiones_proceso alter column modelo_bpmn set not null");
        }
    }

    private static List<Long> sinBpmn(Connection conexion) throws SQLException {
        List<Long> versiones = new ArrayList<>();
        try (Statement sentencia = conexion.createStatement();
                ResultSet filas = sentencia.executeQuery(
                        "select id from versiones_proceso where modelo_bpmn is null order by id")) {
            while (filas.next()) {
                versiones.add(filas.getLong(1));
            }
        }
        return versiones;
    }

    /** Una version: su instantanea con los bpmn_id, y el BPMN que sale de ella. */
    public static void convertir(Connection conexion, long version) throws SQLException {
        String definicion;
        try (PreparedStatement consulta = conexion.prepareStatement(
                "select definicion from versiones_proceso where id = ?")) {
            consulta.setLong(1, version);
            try (ResultSet fila = consulta.executeQuery()) {
                if (!fila.next()) {
                    return;
                }
                definicion = fila.getString(1);
            }
        }
        ObjectNode raiz = (ObjectNode) JSON.readTree(definicion);
        for (Map.Entry<String, String> lista : CONSULTAS.entrySet()) {
            JsonNode elementos = raiz.get(lista.getKey());
            if (elementos instanceof ArrayNode arreglo) {
                raiz.set(lista.getKey(), conBpmnId(conexion, lista.getValue(), arreglo));
            }
        }
        DiagramaResponse diagrama = JSON.treeToValue(raiz, DiagramaResponse.class);
        byte[] bpmn = EscritorBpmn.escribir(diagrama, GeometriaDelDiagrama.de(diagrama));
        try (PreparedStatement actualizacion = conexion.prepareStatement(
                "update versiones_proceso set definicion = ?, modelo_bpmn = ? where id = ?")) {
            actualizacion.setString(1, JSON.writeValueAsString(raiz));
            actualizacion.setString(2, new String(bpmn, StandardCharsets.UTF_8));
            actualizacion.setLong(3, version);
            actualizacion.executeUpdate();
        }
    }

    /** Los mismos elementos, con su bpmn_id justo despues del id, como los escribe hoy el diagrama. */
    private static ArrayNode conBpmnId(Connection conexion, String consulta, ArrayNode elementos)
            throws SQLException {
        Map<Long, String> bpmnIds = bpmnIds(conexion, consulta, elementos);
        ArrayNode nuevos = JSON.createArrayNode();
        for (JsonNode elemento : elementos) {
            if (!(elemento instanceof ObjectNode objeto) || objeto.hasNonNull("bpmnId") || !objeto.hasNonNull("id")) {
                nuevos.add(elemento);
                continue;
            }
            ObjectNode nuevo = JSON.createObjectNode();
            for (Map.Entry<String, JsonNode> campo : objeto.properties()) {
                nuevo.set(campo.getKey(), campo.getValue());
                if ("id".equals(campo.getKey())) {
                    nuevo.put("bpmnId", bpmnIds.get(campo.getValue().asLong()));
                }
            }
            nuevos.add(nuevo);
        }
        return nuevos;
    }

    private static Map<Long, String> bpmnIds(Connection conexion, String sql, ArrayNode elementos)
            throws SQLException {
        List<Long> ids = new ArrayList<>();
        for (JsonNode elemento : elementos) {
            if (elemento.hasNonNull("id")) {
                ids.add(elemento.get("id").asLong());
            }
        }
        Map<Long, String> bpmnIds = new HashMap<>();
        if (ids.isEmpty()) {
            return bpmnIds;
        }
        try (PreparedStatement consulta = conexion.prepareStatement(sql)) {
            Array arreglo = conexion.createArrayOf("bigint", ids.toArray());
            consulta.setArray(1, arreglo);
            try (ResultSet filas = consulta.executeQuery()) {
                while (filas.next()) {
                    bpmnIds.put(filas.getLong(1), filas.getString(2));
                }
            }
        }
        return bpmnIds;
    }
}
