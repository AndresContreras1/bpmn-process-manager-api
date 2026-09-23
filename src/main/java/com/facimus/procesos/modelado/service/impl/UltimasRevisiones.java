package com.facimus.procesos.modelado.service.impl;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import com.facimus.procesos.modelado.dto.response.RevisionResponse;

/**
 * La ultima revision de cada proceso, junto con el diagrama que se reviso. Si el diagrama no cambio, volver a
 * pedirla devuelve la misma sin llamar al modelo: la respuesta seria igual y la llamada se cobra. Recuerda un
 * numero acotado de procesos y olvida primero los que menos se piden. Vive en esta instancia; con varias, cada una
 * llevaria la suya.
 */
class UltimasRevisiones {

    private record Guardada(String diagrama, RevisionResponse revision) {
    }

    private final Map<String, Guardada> revisiones;

    UltimasRevisiones(int maximo) {
        this.revisiones = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Guardada> menosUsada) {
                return size() > maximo;
            }
        };
    }

    /** La revision guardada para ese proceso, solo si su diagrama sigue siendo el mismo. */
    synchronized Optional<RevisionResponse> buscar(String clave, String diagrama) {
        Guardada guardada = revisiones.get(clave);
        if (guardada == null || !guardada.diagrama().equals(diagrama)) {
            return Optional.empty();
        }
        return Optional.of(guardada.revision());
    }

    synchronized void guardar(String clave, String diagrama, RevisionResponse revision) {
        revisiones.put(clave, new Guardada(diagrama, revision));
    }
}
