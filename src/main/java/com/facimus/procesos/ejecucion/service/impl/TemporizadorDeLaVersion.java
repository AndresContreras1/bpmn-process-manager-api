package com.facimus.procesos.ejecucion.service.impl;

import com.facimus.procesos.ejecucion.model.ClaseTemporizador;
import com.facimus.procesos.modelado.model.TipoEvento;
import com.facimus.procesos.modelado.model.TipoTemporizador;

/**
 * PR 68: un evento de temporizador de la version, con lo que hace falta para armarlo: que clase de evento es, como dice
 * cuando salta y, si es de borde, de que actividad cuelga y si la interrumpe. Sale de la instantanea, como el resto
 * del grafo (los datos estan en el modelo desde el PR 55).
 */
record TemporizadorDeLaVersion(Long nodoId, String nombre, TipoEvento evento, TipoTemporizador tipo,
        String expresion, Long adjuntoAId, boolean interrumpe) {

    ClaseTemporizador clase() {
        return switch (evento) {
            case TEMPORIZADOR_INICIO -> ClaseTemporizador.INICIO;
            case TEMPORIZADOR_BORDE -> ClaseTemporizador.BORDE;
            default -> ClaseTemporizador.INTERMEDIO;
        };
    }

    /**
     * Si dice cuando salta. Uno que no lo dice no se arma: el diagnostico no deja publicar eso (E-16), y un caso parado
     * para siempre en una espera sin hora seria peor que uno que sigue.
     */
    boolean diceCuando() {
        return tipo != null && expresion != null && !expresion.isBlank();
    }

    /** Lo que puede volver a saltar: un ciclo de inicio o de un borde que no interrumpe. */
    boolean seRepite() {
        return tipo == TipoTemporizador.CICLO && (evento == TipoEvento.TEMPORIZADOR_INICIO || !interrumpe);
    }
}
