package com.facimus.procesos.ejecucion.service.impl;

import java.util.List;
import java.util.Optional;

import com.facimus.procesos.modelado.model.AccionSiFalla;
import com.facimus.procesos.modelado.model.CampoDeMensaje;
import com.facimus.procesos.modelado.model.Integracion;
import com.facimus.procesos.modelado.model.PoliticaSinCaso;
import com.facimus.procesos.modelado.model.TipoDestino;

/**
 * Un mensaje de la version publicada, visto como lo necesita la ejecucion: a que nodo del pool de la tienda esta
 * anclado, que campos viajan dentro, con que nombre entra el cuerpo a las variables del caso, a que participante
 * va y con que clase de socio habla, que hacer si el envio falla y con que clave se sabe a que caso corresponde.
 *
 * <p>El participante destino viaja por nombre y no por id: lo que se guarda en la bandeja de salida tiene que
 * seguir leyendose dentro de un ano, cuando el pool a lo mejor ya no existe en el modelo vivo. Lo mismo con la
 * respuesta esperada, que se nombra en vez de apuntarse.
 */
record MensajeDeLaVersion(Long id, String nombre, Long nodoOrigenId, Long nodoDestinoId, Long poolOrigenId,
        Long poolDestinoId, String poolDestinoNombre, Integracion integracion, TipoDestino tipoDestino,
        AccionSiFalla siFalla, Long nodoManejoErrorId, List<CampoDeMensaje> campos, String variable,
        String respuestaEsperada, String campoDeCorrelacion, PoliticaSinCaso sinCaso, boolean origenExterno) {

    /** Que hace el proceso si el envio no llega; sin decir nada, sigue por donde iba. */
    AccionSiFalla siFallaOContinuar() {
        return siFalla == null ? AccionSiFalla.CONTINUAR : siFalla;
    }

    /** Que hacer con este mensaje si llega y no corresponde a ningun caso; sin decir nada, se descarta. */
    PoliticaSinCaso sinCasoODescartar() {
        return sinCaso == null ? PoliticaSinCaso.DESCARTAR : sinCaso;
    }

    /**
     * El nombre con el que el cuerpo entra a las variables del caso. Un mensaje que no lo declara entra bajo el
     * suyo en camello, que es mejor que perderlo: quien escribio la condicion tiene algo que nombrar.
     */
    String variableODelNombre() {
        return variable == null || variable.isBlank() ? VariablesDelCaso.enCamello(nombre) : variable;
    }

    /** La actividad que atiende un envio fallido, solo cuando eso es lo que el mensaje dice que hay que hacer. */
    Optional<Long> nodoQueManejaElError() {
        return siFallaOContinuar().necesitaActividad() ? Optional.ofNullable(nodoManejoErrorId) : Optional.empty();
    }
}
