package com.facimus.procesos.modelado.service.impl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.facimus.procesos.gestion.dto.response.ProcesoResponse;
import com.facimus.procesos.modelado.dto.response.DiagramaResponse;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * El diagrama reducido a lo que lo dibuja, para poder compararlo consigo mismo en otro momento. Dos diagramas con la
 * misma forma canonica tienen la misma huella, aunque se hayan guardado en dias distintos o por personas distintas.
 *
 * <p>De cada elemento entra todo menos lo que cambia sin que cambie el dibujo: la version optimista, quien lo creo o
 * lo guardo y cuando. Asi un campo nuevo en un DTO entra solo en la huella y nadie tiene que acordarse de anadirlo.
 * Del proceso entra solo su nombre, su descripcion y su categoria: el estado y la version publicada cambian al
 * publicar, y meterlos dejaria el borrador pendiente en el mismo momento de publicarlo.</p>
 */
final class DiagramaCanonico {

    /** Lo que cuenta la auditoria, no el modelo. */
    private static final List<String> VOLATILES =
            List.of("version", "creadoPor", "fechaCreacion", "modificadoPor", "fechaModificacion");

    private DiagramaCanonico() {
    }

    static String de(DiagramaResponse diagrama, JsonMapper json) {
        ProcesoResponse proceso = diagrama.proceso();
        ObjectNode raiz = json.createObjectNode();
        raiz.put("nombre", proceso.nombre());
        raiz.put("descripcion", proceso.descripcion());
        raiz.put("categoria", proceso.categoria());
        raiz.set("pools", elementos(diagrama.pools(), json));
        raiz.set("lanes", elementos(diagrama.lanes(), json));
        raiz.set("actividades", elementos(diagrama.actividades(), json));
        raiz.set("gateways", elementos(diagrama.gateways(), json));
        raiz.set("eventos", elementos(diagrama.eventos(), json));
        raiz.set("arcos", elementos(diagrama.arcos(), json));
        raiz.set("mensajes", elementos(diagrama.mensajes(), json));
        raiz.set("correlaciones", elementos(diagrama.correlaciones(), json));
        return json.writeValueAsString(raiz);
    }

    /** Ordenados por id: que una consulta devuelva las filas en otro orden no es un cambio del diagrama. */
    private static ArrayNode elementos(List<?> lista, JsonMapper json) {
        List<ObjectNode> nodos = new ArrayList<>();
        for (Object elemento : lista) {
            ObjectNode nodo = (ObjectNode) json.valueToTree(elemento);
            VOLATILES.forEach(nodo::remove);
            nodos.add(nodo);
        }
        nodos.sort(Comparator.comparing(nodo -> nodo.get("id").longValue()));
        ArrayNode arreglo = json.createArrayNode();
        nodos.forEach(arreglo::add);
        return arreglo;
    }
}
