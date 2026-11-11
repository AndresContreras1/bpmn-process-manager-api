package com.facimus.procesos.modelado.bpmn;

import java.util.List;
import java.util.Map;

import com.facimus.procesos.modelado.model.AccionSiFalla;
import com.facimus.procesos.modelado.model.CampoDeMensaje;
import com.facimus.procesos.modelado.model.Integracion;
import com.facimus.procesos.modelado.model.PoliticaSinCaso;
import com.facimus.procesos.modelado.model.TipoActividad;
import com.facimus.procesos.modelado.model.TipoDestino;
import com.facimus.procesos.modelado.model.TipoEvento;
import com.facimus.procesos.modelado.model.TipoGateway;
import com.facimus.procesos.modelado.model.TipoParticipante;

/**
 * PR 54: lo que un archivo pide, ya en los terminos del modelo y con sus reglas cumplidas. Cada elemento se nombra
 * por su bpmn_id y se refiere a los demas por el suyo: aplicarlo es buscar cada uno en el borrador y crearlo, editarlo
 * o darlo de baja. El pool de la organizacion es siempre el primero.
 *
 * @param extensiones lo ajeno de cada elemento, por la clave de {@code extension_xml}
 */
public record PlanBpmn(String nombre, String descripcion, String categoria, List<Pool> pools, List<Lane> lanes,
        List<Nodo> nodos, List<Arco> arcos, List<Mensaje> mensajes, Map<String, String> extensiones,
        InformeBpmn informe) {

    public record Pool(String bpmnId, String nombre, TipoParticipante tipo, Integracion integracion, int orden,
            boolean cajaNegra) {
    }

    /** @param rol el nombre del rol de proceso que trabaja la lane */
    public record Lane(String bpmnId, String pool, String nombre, String rol, int orden) {
    }

    public enum Clase {
        ACTIVIDAD, GATEWAY, EVENTO
    }

    /**
     * Un nodo y su posicion relativa a su lane, como la guarda el modelo: la x comun al diagrama y la y de su lane,
     * con la menor de cada una en cero.
     */
    public record Nodo(String bpmnId, String lane, Clase clase, TipoActividad tipoActividad, TipoGateway tipoGateway,
            TipoEvento tipoEvento, String nombre, String descripcion, int x, int y) {
    }

    public record Arco(String bpmnId, String pool, String origen, String destino, String etiqueta, String condicion,
            boolean porDefecto, int orden) {
    }

    /** Un flujo de mensaje: los nodos son nulos donde el mensaje sale o llega al borde del pool. */
    public record Mensaje(String bpmnId, String nombre, String contenido, String poolOrigen, String poolDestino,
            String nodoOrigen, String nodoDestino, TipoDestino tipoDestino, AccionSiFalla siFalla,
            String manejoError, boolean origenExterno, List<CampoDeMensaje> campos, String usoDeLosDatos,
            String variable, String respuesta, Correlacion correlacion) {
    }

    public record Correlacion(String criterio, String campo, PoliticaSinCaso sinCaso) {
    }
}
