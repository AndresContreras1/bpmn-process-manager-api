package com.facimus.procesos.ejecucion.service.impl;

import com.facimus.procesos.ejecucion.model.TipoNodoCaso;
import com.facimus.procesos.modelado.model.TipoActividad;
import com.facimus.procesos.modelado.model.TipoEvento;
import com.facimus.procesos.modelado.model.TipoGateway;

/**
 * Un nodo de la version publicada, visto como lo necesita el motor: que es, en que lane esta y a que rol pertenece
 * esa lane. El subtipo llega como texto porque son tres enumerados distintos y es lo que se copia a cada paso del
 * caso, donde ya no hay a quien preguntarle.
 */
record NodoDeLaVersion(Long id, String nombre, TipoNodoCaso tipo, String subtipo, Long laneId, Long rolProcesoId) {

    boolean esActividad() {
        return tipo == TipoNodoCaso.ACTIVIDAD;
    }

    boolean esGateway() {
        return tipo == TipoNodoCaso.GATEWAY;
    }

    boolean esEvento() {
        return tipo == TipoNodoCaso.EVENTO;
    }

    /** La actividad que espera a una persona: la unica que aparece en una bandeja. */
    boolean esTareaDeUsuario() {
        return esActividad() && TipoActividad.USUARIO.name().equals(subtipo);
    }

    boolean esGatewayDe(TipoGateway tipoGateway) {
        return esGateway() && tipoGateway.name().equals(subtipo);
    }

    boolean esEventoDe(TipoEvento tipoEvento) {
        return esEvento() && tipoEvento.name().equals(subtipo);
    }

    /** Por donde empieza un caso; el de mensaje lo abre un entrante, no una persona. */
    boolean empiezaElProceso() {
        return esEvento() && TipoEvento.valueOf(subtipo).empiezaElProceso();
    }

    /** Por donde muere un token: de un evento de fin no sale nada. */
    boolean terminaElProceso() {
        return esEvento() && TipoEvento.valueOf(subtipo).terminaElProceso();
    }

    /** El exclusivo y el inclusivo eligen por condicion; el paralelo sigue todas sus salidas. */
    boolean eligePorCondicion() {
        return esGateway() && TipoGateway.valueOf(subtipo).eligePorCondicion();
    }
}
