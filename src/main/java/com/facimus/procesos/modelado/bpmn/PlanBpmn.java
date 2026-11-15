package com.facimus.procesos.modelado.bpmn;

import java.util.List;
import java.util.Map;

import com.facimus.procesos.common.formularios.Formulario;
import com.facimus.procesos.gestion.model.ModoProceso;
import com.facimus.procesos.modelado.model.AccionSiFalla;
import com.facimus.procesos.modelado.model.CampoDeMensaje;
import com.facimus.procesos.modelado.model.DireccionAsociacion;
import com.facimus.procesos.modelado.model.Integracion;
import com.facimus.procesos.modelado.model.PoliticaSinCaso;
import com.facimus.procesos.modelado.model.TipoActividad;
import com.facimus.procesos.modelado.model.TipoArtefacto;
import com.facimus.procesos.modelado.model.TipoAsociacion;
import com.facimus.procesos.modelado.model.TipoDestino;
import com.facimus.procesos.modelado.model.TipoEvento;
import com.facimus.procesos.modelado.model.TipoGateway;
import com.facimus.procesos.modelado.model.TipoParticipante;
import com.facimus.procesos.modelado.model.TipoTemporizador;

/**
 * PR 54: lo que un archivo pide, ya en los terminos del modelo y con sus reglas cumplidas. Cada elemento se nombra
 * por su bpmn_id y se refiere a los demas por el suyo: aplicarlo es buscar cada uno en el borrador y crearlo, editarlo
 * o darlo de baja. El pool de la organizacion es siempre el primero.
 *
 * @param extensiones lo ajeno de cada elemento, por la clave de {@code extension_xml}
 */
public record PlanBpmn(String nombre, String descripcion, String categoria, List<Pool> pools, List<Lane> lanes,
        List<Nodo> nodos, List<Arco> arcos, List<Mensaje> mensajes, Map<String, String> extensiones,
        InformeBpmn informe, ModoProceso modo, List<Artefacto> artefactos, List<Asociacion> asociaciones) {

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
            TipoEvento tipoEvento, String nombre, String descripcion, int x, int y, String adjuntoA,
            Boolean interrumpe, TipoTemporizador tipoTemporizador, String temporizador, String padre,
            Long procesoLlamado, Formulario formulario) {

        /** El de antes del PR 69, sin formulario. */
        public Nodo(String bpmnId, String lane, Clase clase, TipoActividad tipoActividad, TipoGateway tipoGateway,
                TipoEvento tipoEvento, String nombre, String descripcion, int x, int y, String adjuntoA,
                Boolean interrumpe, TipoTemporizador tipoTemporizador, String temporizador, String padre,
                Long procesoLlamado) {
            this(bpmnId, lane, clase, tipoActividad, tipoGateway, tipoEvento, nombre, descripcion, x, y, adjuntoA,
                    interrumpe, tipoTemporizador, temporizador, padre, procesoLlamado, null);
        }

        /** El mismo nodo en otro sitio. */
        Nodo en(String otraLane, int otraX, int otraY) {
            return new Nodo(bpmnId, otraLane, clase, tipoActividad, tipoGateway, tipoEvento, nombre, descripcion, otraX,
                    otraY, adjuntoA, interrumpe, tipoTemporizador, temporizador, padre, procesoLlamado, formulario);
        }
    }

    /**
     * PR 55: un artefacto. En una lane, su esquina con el mismo marco que los nodos de ella; suelto, relativo a los
     * demas sueltos, con el menor en cero.
     */
    public record Artefacto(String bpmnId, TipoArtefacto tipo, String texto, String pool, String lane, int x, int y,
            int ancho, int alto) {
    }

    /** PR 55: una asociacion: sus extremos son bpmn_id de nodos, flujos o artefactos del plan. */
    public record Asociacion(String bpmnId, TipoAsociacion tipo, String origen, String destino,
            DireccionAsociacion direccion) {
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
