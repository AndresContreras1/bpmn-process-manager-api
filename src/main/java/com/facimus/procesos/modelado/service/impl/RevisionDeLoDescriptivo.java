package com.facimus.procesos.modelado.service.impl;

import java.util.Optional;

import com.facimus.procesos.modelado.dto.response.AsociacionResponse;
import com.facimus.procesos.modelado.model.CodigoDeDiagnostico;
import com.facimus.procesos.modelado.model.TipoActividad;

/**
 * PR 55: lo que BPMN tiene para describir un proceso y el motor todavia no corre. En un proceso documental todo eso
 * es valido; en uno que va a correr, una tarea sin tipo o un temporizador dejarian al caso sin saber que hacer.
 */
final class RevisionDeLoDescriptivo {

    private RevisionDeLoDescriptivo() {
    }

    static void revisar(MapaDelDiagrama mapa, Hallazgos hallazgos) {
        for (NodoDelDiagrama nodo : mapa.nodos()) {
            if (nodo.esActividad() && nodo.tipoActividad() == TipoActividad.SIN_TIPO) {
                hallazgos.anotar(CodigoDeDiagnostico.E17, nodo.etiqueta(), nodo.id(),
                        "Esta tarea no dice de que tipo es, y en un proceso que corre nadie sabria quien la hace.",
                        "Dale un tipo (de usuario, de servicio, de envio o de recepcion), o pasa el proceso a "
                                + "documental.");
            }
            if (nodo.esTemporizador()) {
                temporizador(nodo, hallazgos);
            }
            if (nodo.esActividad() && nodo.tipoActividad().esSubproceso()) {
                hallazgos.anotar(CodigoDeDiagnostico.E19, nodo.etiqueta(), nodo.id(),
                        "Un subproceso no corre en un proceso ejecutable.",
                        "Conviertelo: lleva su contenido a otro proceso y usa una actividad de llamada, o pasa este a "
                                + "documental.");
            }
            if (nodo.esActividad() && nodo.tipoActividad() == TipoActividad.LLAMADA) {
                llamada(nodo, hallazgos);
            }
        }
        for (AsociacionResponse asociacion : mapa.diagrama().asociaciones()) {
            asociacion(mapa, asociacion, hallazgos);
        }
    }

    /** E-20 y A-16: a que proceso llama, y que todavia no lo corre. */
    private static void llamada(NodoDelDiagrama nodo, Hallazgos hallazgos) {
        if (nodo.procesoLlamadoId() == null) {
            hallazgos.anotar(CodigoDeDiagnostico.E20, nodo.etiqueta(), nodo.id(),
                    "Esta actividad de llamada no dice que proceso corre.",
                    "Elige el proceso de la tienda que llama.");
            return;
        }
        hallazgos.anotar(CodigoDeDiagnostico.A16, nodo.etiqueta(), nodo.id(),
                "Las actividades de llamada todavia no corren el proceso que llaman: el caso sigue de largo.",
                "Dejala asi si el proceso llamado solo documenta ese paso.");
    }

    /** E-16 y E-18: cuando salta, y que todavia no salta. */
    private static void temporizador(NodoDelDiagrama nodo, Hallazgos hallazgos) {
        Optional<String> problema = ExpresionDeTemporizador.problema(nodo.tipoTemporizador(), nodo.temporizador());
        problema.ifPresent(detalle -> hallazgos.anotar(CodigoDeDiagnostico.E16, nodo.etiqueta(), nodo.id(),
                detalle, "Escribe una duracion (PT2H), una fecha (2026-12-24T09:00:00Z) o un ciclo (R3/PT1H), en "
                        + "ISO 8601."));
        hallazgos.anotar(CodigoDeDiagnostico.E18, nodo.etiqueta(), nodo.id(),
                "Los temporizadores todavia no corren: el caso no esperaria aqui, o no saltaria nunca.",
                "Pasa el proceso a documental mientras tanto, o quita el temporizador.");
    }

    /** A-15: una asociacion nombra sus extremos por bpmn_id, y uno pudo irse del diagrama. */
    private static void asociacion(MapaDelDiagrama mapa, AsociacionResponse asociacion, Hallazgos hallazgos) {
        String falta = !mapa.existe(asociacion.origen()) ? asociacion.origen()
                : !mapa.existe(asociacion.destino()) ? asociacion.destino() : null;
        if (falta != null) {
            hallazgos.anotar(CodigoDeDiagnostico.A15, "Asociacion " + asociacion.bpmnId(), asociacion.id(),
                    "Esta asociacion une algo que ya no esta en el diagrama: " + falta + ".",
                    "Borrala, o unela a otro elemento.");
        }
    }
}
