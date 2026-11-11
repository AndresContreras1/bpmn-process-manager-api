package com.facimus.procesos.modelado.service.impl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.facimus.procesos.gestion.model.ModoProceso;
import com.facimus.procesos.modelado.dto.response.ComparacionResponse;
import com.facimus.procesos.modelado.dto.response.ComparacionResponse.Cambiado;
import com.facimus.procesos.modelado.dto.response.ComparacionResponse.Campo;
import com.facimus.procesos.modelado.dto.response.ComparacionResponse.Elemento;
import com.facimus.procesos.modelado.dto.response.CorrelacionResponse;
import com.facimus.procesos.modelado.dto.response.DiagramaResponse;
import com.facimus.procesos.modelado.dto.response.MensajeResponse;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * PR 57: la diferencia entre dos diagramas, por bpmn_id. Es una funcion pura sobre la misma forma de la huella: lo que
 * la huella ignora, aqui tampoco es un cambio. Los ids de la base no dicen nada entre dos momentos, porque restaurar
 * crea filas nuevas: las referencias se comparan por el bpmn_id al que apuntan.
 */
final class ComparacionDeDiagramas {

    private static final String PROCESO = "proceso";

    /** Lo que no es del modelo: la auditoria, y los ids de la base. */
    private static final List<String> FUERA = List.of("id", "bpmnId", "version", "creadoPor", "fechaCreacion",
            "modificadoPor", "fechaModificacion", "rolProcesoId", "procesoId");

    /** Lo que cambia el dibujo y no el proceso. */
    private static final Set<String> GEOMETRIA = Set.of("posicionX", "posicionY", "ancho", "alto");

    private ComparacionDeDiagramas() {
    }

    /** Un elemento listo para comparar. */
    private record Plano(String bpmnId, String tipo, String nombre, ObjectNode campos) {
    }

    static ComparacionResponse comparar(String de, DiagramaResponse primero, String a, DiagramaResponse segundo,
            JsonMapper json) {
        Map<String, Plano> antes = planos(primero, json);
        Map<String, Plano> despues = planos(segundo, json);
        List<Elemento> agregados = new ArrayList<>();
        List<Elemento> quitados = new ArrayList<>();
        List<Cambiado> cambiados = new ArrayList<>();
        List<Elemento> movidos = new ArrayList<>();
        despues.forEach((clave, plano) -> {
            if (!antes.containsKey(clave)) {
                agregados.add(elemento(plano));
            }
        });
        antes.forEach((clave, plano) -> {
            Plano otro = despues.get(clave);
            if (otro == null) {
                quitados.add(elemento(plano));
                return;
            }
            List<Campo> campos = campos(plano.campos(), otro.campos());
            if (campos.stream().anyMatch(campo -> !GEOMETRIA.contains(campo.campo()))) {
                cambiados.add(new Cambiado(otro.bpmnId(), otro.tipo(), otro.nombre(), campos));
            } else if (!campos.isEmpty()) {
                movidos.add(elemento(otro));
            }
        });
        Comparator<Elemento> orden = Comparator.comparing(Elemento::tipo).thenComparing(Elemento::bpmnId);
        agregados.sort(orden);
        quitados.sort(orden);
        movidos.sort(orden);
        cambiados.sort(Comparator.comparing(Cambiado::tipo).thenComparing(Cambiado::bpmnId));
        return new ComparacionResponse(de, a, agregados, quitados, cambiados, movidos);
    }

    private static Elemento elemento(Plano plano) {
        return new Elemento(plano.bpmnId(), plano.tipo(), plano.nombre());
    }

    private static List<Campo> campos(ObjectNode antes, ObjectNode despues) {
        Set<String> nombres = new TreeSet<>(antes.propertyNames());
        nombres.addAll(despues.propertyNames());
        List<Campo> campos = new ArrayList<>();
        for (String nombre : nombres) {
            JsonNode uno = antes.get(nombre);
            JsonNode otro = despues.get(nombre);
            if (uno == null || !uno.equals(otro)) {
                campos.add(new Campo(nombre, texto(uno), texto(otro)));
            }
        }
        return campos;
    }

    private static String texto(JsonNode valor) {
        if (valor == null) {
            return null;
        }
        return valor.isValueNode() ? valor.asString() : valor.toString();
    }

    private static Map<String, Plano> planos(DiagramaResponse diagrama, JsonMapper json) {
        // Cada clase de elemento numera sus ids en su tabla: un mismo numero puede ser un nodo y un flujo
        Map<Long, String> pools = new HashMap<>();
        Map<Long, String> lanes = new HashMap<>();
        Map<Long, String> nodos = new HashMap<>();
        Map<Long, String> arcos = new HashMap<>();
        Map<Long, String> mensajes = new HashMap<>();
        diagrama.pools().forEach(uno -> pools.put(uno.id(), clave(uno.bpmnId(), "Participant", uno.id())));
        diagrama.lanes().forEach(uno -> lanes.put(uno.id(), clave(uno.bpmnId(), "Lane", uno.id())));
        diagrama.actividades().forEach(uno -> nodos.put(uno.id(), clave(uno.bpmnId(), "Activity", uno.id())));
        diagrama.gateways().forEach(uno -> nodos.put(uno.id(), clave(uno.bpmnId(), "Gateway", uno.id())));
        diagrama.eventos().forEach(uno -> nodos.put(uno.id(), clave(uno.bpmnId(), "Event", uno.id())));
        diagrama.arcos().forEach(uno -> arcos.put(uno.id(), clave(uno.bpmnId(), "Flow", uno.id())));
        diagrama.mensajes().forEach(uno -> mensajes.put(uno.id(), clave(uno.bpmnId(), "MessageFlow", uno.id())));
        Map<String, Map<Long, String>> referencias = Map.ofEntries(Map.entry("laneId", lanes), Map.entry("poolId", pools),
                Map.entry("poolOrigenId", pools), Map.entry("poolDestinoId", pools), Map.entry("origenId", nodos),
                Map.entry("destinoId", nodos), Map.entry("nodoOrigenId", nodos), Map.entry("nodoDestinoId", nodos),
                Map.entry("nodoManejoErrorId", nodos), Map.entry("adjuntoAId", nodos), Map.entry("padreId", nodos),
                Map.entry("mensajeId", mensajes), Map.entry("respuestaEsperadaId", mensajes));

        // La posicion de un evento de borde o de un subproceso con contenido no dice nada: no se dibujan ahi
        Set<Long> sinPosicion = new HashSet<>();
        diagrama.eventos().stream().filter(evento -> evento.adjuntoAId() != null)
                .forEach(evento -> sinPosicion.add(evento.id()));
        diagrama.actividades().forEach(uno -> agregar(sinPosicion, uno.padreId()));
        diagrama.eventos().forEach(uno -> agregar(sinPosicion, uno.padreId()));
        diagrama.gateways().forEach(uno -> agregar(sinPosicion, uno.padreId()));

        Map<String, Plano> planos = new LinkedHashMap<>();
        planos.put(PROCESO, proceso(diagrama, json));
        for (var uno : diagrama.pools()) {
            agregar(planos, pools.get(uno.id()), "POOL", uno.nombre(), campos(uno, referencias, false, json));
        }
        for (var uno : diagrama.lanes()) {
            agregar(planos, lanes.get(uno.id()), "LANE", uno.nombre(), campos(uno, referencias, false, json));
        }
        for (var uno : diagrama.actividades()) {
            agregar(planos, nodos.get(uno.id()), "ACTIVIDAD", uno.nombre(),
                    campos(uno, referencias, sinPosicion.contains(uno.id()), json));
        }
        for (var uno : diagrama.gateways()) {
            agregar(planos, nodos.get(uno.id()), "GATEWAY", uno.nombre(), campos(uno, referencias, false, json));
        }
        for (var uno : diagrama.eventos()) {
            agregar(planos, nodos.get(uno.id()), "EVENTO", uno.nombre(),
                    campos(uno, referencias, sinPosicion.contains(uno.id()), json));
        }
        for (var uno : diagrama.arcos()) {
            agregar(planos, arcos.get(uno.id()), "ARCO", uno.etiqueta(), campos(uno, referencias, false, json));
        }
        for (var uno : diagrama.mensajes()) {
            agregar(planos, mensajes.get(uno.id()), "MENSAJE", uno.nombre(), campos(uno, referencias, false, json));
        }
        correlaciones(planos, diagrama, mensajes, referencias, json);
        for (var artefacto : diagrama.artefactos()) {
            agregar(planos, artefacto.bpmnId(), "ARTEFACTO", artefacto.texto(),
                    campos(artefacto, referencias, false, json));
        }
        for (var asociacion : diagrama.asociaciones()) {
            agregar(planos, asociacion.bpmnId(), "ASOCIACION", null, campos(asociacion, referencias, false, json));
        }
        return planos;
    }

    private static void agregar(Map<String, Plano> planos, String clave, String tipo, String nombre,
            ObjectNode campos) {
        planos.put(clave, new Plano(clave, tipo, nombre, campos));
    }

    private static void agregar(Set<Long> conjunto, Long valor) {
        if (valor != null) {
            conjunto.add(valor);
        }
    }

    private static Plano proceso(DiagramaResponse diagrama, JsonMapper json) {
        ObjectNode campos = json.createObjectNode();
        campos.put("nombre", diagrama.proceso().nombre());
        campos.put("descripcion", diagrama.proceso().descripcion());
        campos.put("categoria", diagrama.proceso().categoria());
        ModoProceso modo = diagrama.proceso().modo();
        campos.put("modo", modo.name());
        return new Plano(PROCESO, "PROCESO", diagrama.proceso().nombre(), campos);
    }

    /** Una correlacion no tiene bpmn_id: es del mensaje, y se compara por el. */
    private static void correlaciones(Map<String, Plano> planos, DiagramaResponse diagrama, Map<Long, String> mensajes,
            Map<String, Map<Long, String>> referencias, JsonMapper json) {
        Map<Long, String> nombres = new HashMap<>();
        for (MensajeResponse mensaje : diagrama.mensajes()) {
            nombres.put(mensaje.id(), mensaje.nombre());
        }
        for (CorrelacionResponse correlacion : diagrama.correlaciones()) {
            String mensaje = mensajes.get(correlacion.mensajeId());
            planos.put("Correlation_" + mensaje, new Plano(mensaje, "CORRELACION", nombres.get(correlacion.mensajeId()),
                    campos(correlacion, referencias, false, json)));
        }
    }

    private static ObjectNode campos(Object elemento, Map<String, Map<Long, String>> referencias, boolean sinPosicion,
            JsonMapper json) {
        ObjectNode nodo = (ObjectNode) json.valueToTree(elemento);
        FUERA.forEach(nodo::remove);
        if (sinPosicion) {
            GEOMETRIA.forEach(nodo::remove);
        }
        for (String nombre : new ArrayList<>(nodo.propertyNames())) {
            JsonNode valor = nodo.get(nombre);
            if (valor.isNull()) {
                nodo.remove(nombre);
            } else if (referencias.containsKey(nombre)) {
                nodo.put(nombre, referencias.get(nombre).getOrDefault(valor.longValue(), valor.asString()));
            }
        }
        return nodo;
    }

    private static String clave(String bpmnId, String tipo, Long id) {
        return bpmnId != null ? bpmnId : tipo + "_" + id;
    }
}
