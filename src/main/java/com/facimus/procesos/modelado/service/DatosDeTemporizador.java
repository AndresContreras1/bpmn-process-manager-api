package com.facimus.procesos.modelado.service;

import com.facimus.procesos.modelado.model.TipoTemporizador;

/**
 * PR 55: lo que un evento de temporizador dice de mas: cuando salta y, si es de borde, a que actividad va pegado y si
 * la interrumpe. Para cualquier otro evento va vacio.
 */
public record DatosDeTemporizador(TipoTemporizador tipo, String expresion, Long adjuntoAId, Boolean interrumpe) {

    public static DatosDeTemporizador ninguno() {
        return new DatosDeTemporizador(null, null, null, null);
    }
}
