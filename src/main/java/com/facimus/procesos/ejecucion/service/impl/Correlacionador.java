package com.facimus.procesos.ejecucion.service.impl;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.facimus.procesos.ejecucion.model.ActividadCaso;
import com.facimus.procesos.ejecucion.model.Caso;
import com.facimus.procesos.ejecucion.model.EstadoActividadCaso;
import com.facimus.procesos.ejecucion.model.EstadoCaso;
import com.facimus.procesos.ejecucion.model.ResultadoCorrelacion;
import com.facimus.procesos.ejecucion.repository.ActividadCasoRepository;
import com.facimus.procesos.ejecucion.repository.CasoRepository;

import lombok.RequiredArgsConstructor;

/**
 * A que caso corresponde un mensaje que llega. Son cuatro finales y se deciden en este orden:
 *
 * <ol>
 *   <li>hay un caso abierto del proceso con esa clave y un nodo esperando ese mensaje: se le entrega;</li>
 *   <li>hay caso, pero todavia no ha llegado a esperarlo: se queda en espera y se reintenta cuando avance;</li>
 *   <li>no hay caso y el mensaje es de los que abren uno: se abre;</li>
 *   <li>no hay caso y el mensaje no abre ninguno: se descarta, escrito, para que se pueda mirar.</li>
 * </ol>
 *
 * <p>Un mensaje sin clave no se entrega a ningun caso por el hecho de llamarse igual: sin clave no se sabe de cual
 * es, y adivinar seria meter la respuesta de un pedido en otro. Se busca solo entre los casos del mismo proceso de
 * la misma tienda, asi que una clave que existe en otro sitio no lo encuentra.
 *
 * <p>El caso vuelve ya bloqueado (D3): quien lo encuentra es quien lo va a mover, y entre encontrarlo y moverlo no
 * puede colarse nadie.
 */
@Component
@RequiredArgsConstructor
class Correlacionador {

    private final CasoRepository casoRepository;
    private final ActividadCasoRepository actividadCasoRepository;

    Correlacion correlacionar(Long empresaId, Long procesoId, GrafoDeVersion vigente, MensajeDeLaVersion mensaje,
            String clave) {
        Optional<Caso> caso = buscarYBloquear(empresaId, procesoId, clave);
        if (caso.isEmpty()) {
            return sinCaso(vigente, mensaje);
        }
        Caso suCaso = caso.orElseThrow();
        return quienEspera(suCaso, mensaje)
                .map(token -> new Correlacion(ResultadoCorrelacion.ENTREGADO_A_CASO, suCaso, token, null))
                .orElseGet(() -> new Correlacion(ResultadoCorrelacion.EN_ESPERA, suCaso, null, null));
    }

    /**
     * El caso abierto del proceso con esa clave, bloqueado. Si hubiera mas de uno con la misma referencia, se toma
     * el primero: la clave de correlacion identifica un pedido, y dos pedidos abiertos con el mismo numero serian
     * un problema de la tienda, no de quien manda el mensaje.
     */
    private Optional<Caso> buscarYBloquear(Long empresaId, Long procesoId, String clave) {
        if (clave == null || clave.isBlank()) {
            return Optional.empty();
        }
        return casoRepository.porReferencia(empresaId, procesoId, clave).stream()
                .filter(caso -> caso.getEstado() == EstadoCaso.ABIERTO)
                .findFirst()
                .flatMap(caso -> casoRepository.bloquear(caso.getId(), empresaId));
    }

    /** El token que esta esperando justo ese mensaje, que es el que esta parado en el nodo que lo espera. */
    private Optional<ActividadCaso> quienEspera(Caso caso, MensajeDeLaVersion mensaje) {
        if (mensaje.nodoDestinoId() == null) {
            return Optional.empty();
        }
        return actividadCasoRepository.findFirstByCasoIdAndEmpresaIdAndNodoIdAndEstadoOrderByIdAsc(caso.getId(),
                caso.getEmpresa().getId(), mensaje.nodoDestinoId(), EstadoActividadCaso.EN_ESPERA);
    }

    /**
     * Lo que se hace con un mensaje que no corresponde a ningun caso: lo dice su correlacion. Abrir uno solo vale
     * si el mensaje esta anclado a un evento que empieza el proceso; cualquier otro se descarta, por mucho que su
     * correlacion diga que inicia casos.
     */
    private static Correlacion sinCaso(GrafoDeVersion vigente, MensajeDeLaVersion mensaje) {
        if (!mensaje.sinCasoODescartar().abreCaso()) {
            return Correlacion.descartado();
        }
        return Optional.ofNullable(mensaje.nodoDestinoId())
                .flatMap(vigente::nodo)
                .filter(NodoDeLaVersion::empiezaElProceso)
                .map(inicio -> new Correlacion(ResultadoCorrelacion.CASO_NUEVO, null, null, inicio))
                .orElseGet(Correlacion::descartado);
    }
}
