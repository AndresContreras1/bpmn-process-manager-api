package com.facimus.procesos.ejecucion.service.impl;

import java.util.Optional;

import com.facimus.procesos.ejecucion.model.ActividadCaso;
import com.facimus.procesos.ejecucion.model.Caso;
import com.facimus.procesos.ejecucion.model.ResultadoCorrelacion;

/**
 * A donde va un mensaje que llego: el resultado y, segun cual sea, el caso al que corresponde con el token que lo
 * esperaba, o el nodo por el que se abre uno nuevo.
 */
record Correlacion(ResultadoCorrelacion resultado, Caso caso, ActividadCaso token, NodoDeLaVersion inicio) {

    static Correlacion descartado() {
        return new Correlacion(ResultadoCorrelacion.DESCARTADO, null, null, null);
    }

    Optional<Caso> elCaso() {
        return Optional.ofNullable(caso);
    }
}
