package com.facimus.procesos.modelado.bpmn;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.facimus.procesos.common.formularios.Formulario;
import com.facimus.procesos.modelado.dto.response.AsignacionResponse;
import com.facimus.procesos.modelado.model.CampoDeMensaje;
import com.facimus.procesos.modelado.model.DisposicionBpmn.Limites;

/**
 * PR 54: un archivo BPMN tal como se leyo, antes de decidir que se hace con cada cosa. Los tipos de las extensiones
 * {@code bpm:} van como texto: si traen un valor que no existe, la conversion lo anota y pone el de por defecto.
 *
 * @param nombre el de las definiciones o la colaboracion
 * @param mensajes el nombre de cada {@code message}, por su id
 * @param formas los limites de cada {@code BPMNShape}, por el id de su elemento
 * @param verticales los pools y lanes que se dibujan en vertical ({@code isHorizontal="false"})
 * @param noSoportados lo que el archivo trae y el modelo no tiene
 * @param extensiones lo ajeno de cada elemento, ya en su envoltorio ({@link ExtensionesAjenas}), por su clave
 */
public record ModeloLeido(String nombre, String descripcion, String categoria, List<Participante> participantes,
        List<Proceso> procesos, List<FlujoDeMensaje> flujosDeMensaje, Map<String, String> mensajes,
        Map<String, Limites> formas, Set<String> verticales, List<Ajeno> noSoportados,
        Map<String, String> extensiones, List<Artefacto> artefactos, List<Asociacion> asociaciones,
        Set<String> expandidos) {

    public record Participante(String id, String nombre, String proceso, String tipo, String integracion,
            Integer orden) {
    }

    /** Un proceso con sus lanes hoja (las anidadas se aplanan), sus nodos y sus flujos. */
    public record Proceso(String id, String nombre, Boolean ejecutable, List<Lane> lanes, List<Nodo> nodos,
            List<Flujo> flujos, boolean lanesAnidadas) {
    }

    public record Lane(String id, String nombre, String rol, Integer orden, List<String> nodos) {
    }

    /**
     * @param elemento el nombre local del elemento BPMN: {@code userTask}, {@code exclusiveGateway}...
     * @param definiciones las definiciones de evento que trae: {@code messageEventDefinition}...
     * @param adjuntoA de un {@code boundaryEvent}, la actividad a la que va pegado
     * @param interrumpe de un {@code boundaryEvent}, si interrumpe la actividad
     * @param tipoTemporizador {@code timeDuration}, {@code timeDate} o {@code timeCycle}, si dice cuando salta; PR 68:
     *            {@link #DURACION_HABIL} si es un {@code timeDuration} con {@code bpm:habil="true"}
     * @param temporizador la expresion de cuando salta
     * @param padre el {@code subProcess} donde esta, o nulo si esta en el proceso
     * @param llamado el {@code calledElement} de una {@code callActivity}
     * @param formulario PR 69: el {@code bpm:formulario} que trae, o nulo
     * @param limitesIlegibles PR 69: los limites del formulario que no se leyeron como numero, ya dichos
     * @param asignacion PR 70: la {@code bpm:asignacion} que trae, o nula
     */
    public record Nodo(String id, String elemento, String nombre, String descripcion, String porDefecto,
            List<String> definiciones, String adjuntoA, boolean interrumpe, String tipoTemporizador,
            String temporizador, String padre, String llamado, Formulario formulario, List<String> limitesIlegibles,
            AsignacionResponse asignacion) {

        /** El de antes del PR 70, sin asignacion. */
        @SuppressWarnings("java:S107") // lo que se lee de un nodo, por su nombre
        public Nodo(String id, String elemento, String nombre, String descripcion, String porDefecto,
                List<String> definiciones, String adjuntoA, boolean interrumpe, String tipoTemporizador,
                String temporizador, String padre, String llamado, Formulario formulario,
                List<String> limitesIlegibles) {
            this(id, elemento, nombre, descripcion, porDefecto, definiciones, adjuntoA, interrumpe, tipoTemporizador,
                    temporizador, padre, llamado, formulario, limitesIlegibles, null);
        }

        /** PR 68: un {@code timeDuration} que se cuenta en tiempo habil, con el calendario de la tienda. */
        public static final String DURACION_HABIL = "timeDuration+habil";
    }

    /**
     * PR 55: una anotacion, un grupo o un dato.
     *
     * @param elemento el nombre local del elemento BPMN: {@code textAnnotation}, {@code dataObjectReference}...
     * @param proceso el id del proceso donde esta, o nulo si esta en la colaboracion
     * @param objeto de un {@code dataObjectReference}, el {@code dataObject} al que apunta
     */
    public record Artefacto(String id, String elemento, String texto, String proceso, String objeto) {
    }

    /**
     * PR 55: una asociacion o una asociacion de datos.
     *
     * @param elemento {@code association}, {@code dataInputAssociation} o {@code dataOutputAssociation}
     * @param direccion el {@code associationDirection} que traia
     */
    public record Asociacion(String id, String elemento, String origen, String destino, String direccion) {
    }

    public record Flujo(String id, String nombre, String origen, String destino, String condicion, String lenguaje,
            Integer orden) {
    }

    public record FlujoDeMensaje(String id, String nombre, String origen, String destino, String mensaje,
            DatosDelMensaje datos) {
    }

    /** Lo que trae {@code bpm:mensaje}; nulo si el archivo no es de BPMN Process Manager. */
    public record DatosDelMensaje(String contenido, String usoDeLosDatos, List<CampoDeMensaje> campos, String destino,
            String siFalla, String manejoError, boolean origenExterno, String variable, String respuesta,
            Correlacion correlacion) {
    }

    public record Correlacion(String criterio, String campo, String sinCaso) {
    }

    /** Un elemento que el modelo no tiene: su id, su tipo BPMN, su nombre y donde estaba. */
    public record Ajeno(String id, String elemento, String nombre, String donde) {
    }
}
