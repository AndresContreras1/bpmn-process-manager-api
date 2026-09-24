package com.facimus.procesos.modelado.service.impl;

import com.facimus.procesos.modelado.dto.response.ActividadResponse;
import com.facimus.procesos.modelado.dto.response.EventoResponse;
import com.facimus.procesos.modelado.dto.response.GatewayResponse;
import com.facimus.procesos.modelado.model.TipoActividad;
import com.facimus.procesos.modelado.model.TipoEvento;
import com.facimus.procesos.modelado.model.TipoGateway;

/**
 * Un nodo del diagrama visto igual sea actividad, gateway o evento: lo que el diagnostico necesita saber de el sin
 * preguntar de que tipo es. Exactamente uno de los tres tipos viene lleno, el mismo que lo distingue en la base.
 */
record NodoDelDiagrama(Long id, String nombre, Long laneId, TipoActividad tipoActividad, TipoGateway tipoGateway,
        TipoEvento tipoEvento) {

    static NodoDelDiagrama de(ActividadResponse actividad) {
        return new NodoDelDiagrama(actividad.id(), actividad.nombre(), actividad.laneId(), actividad.tipoActividad(),
                null, null);
    }

    static NodoDelDiagrama de(GatewayResponse gateway) {
        return new NodoDelDiagrama(gateway.id(), gateway.nombre(), gateway.laneId(), null, gateway.tipoGateway(),
                null);
    }

    static NodoDelDiagrama de(EventoResponse evento) {
        return new NodoDelDiagrama(evento.id(), evento.nombre(), evento.laneId(), null, null, evento.tipoEvento());
    }

    boolean esActividad() {
        return tipoActividad != null;
    }

    boolean esGateway() {
        return tipoGateway != null;
    }

    boolean esEvento() {
        return tipoEvento != null;
    }

    /** Por donde empieza el proceso: nada llega a un evento de inicio. */
    boolean empiezaElProceso() {
        return esEvento() && tipoEvento.empiezaElProceso();
    }

    /** Por donde termina un camino: de un evento de fin no sale nada. */
    boolean terminaElProceso() {
        return esEvento() && tipoEvento.terminaElProceso();
    }

    /** Si desde este nodo sale un mensaje hacia otro participante. Las mismas reglas que aplica el modelo. */
    boolean puedeEnviarMensajes() {
        return (esActividad() && tipoActividad.puedeEnviar()) || (esEvento() && tipoEvento.puedeEnviar());
    }

    /** Si este nodo se queda esperando un mensaje de otro participante. */
    boolean puedeRecibirMensajes() {
        return (esActividad() && tipoActividad.puedeRecibir()) || (esEvento() && tipoEvento.puedeRecibir());
    }

    /** Si un gateway exclusivo o inclusivo elige por condicion lo que sale de el. */
    boolean eligePorCondicion() {
        return esGateway() && tipoGateway.eligePorCondicion();
    }

    /** Como lo nombra el diagnostico, igual que el panel del editor. */
    String etiqueta() {
        return clase() + " \"" + nombre + "\"";
    }

    private String clase() {
        if (esActividad()) {
            return "Actividad";
        }
        return esGateway() ? "Gateway" : "Evento";
    }
}
