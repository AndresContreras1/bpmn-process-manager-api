package com.facimus.procesos.modelado.service.impl;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.facimus.procesos.common.ReglaNegocioException;

/**
 * R-43: reordenar se hace con la lista completa, no moviendo un elemento de a uno. Asi el cliente manda lo que ve
 * despues de arrastrar y el servidor no tiene que adivinar que paso con los demas; si la lista no trae exactamente
 * los hijos del padre, no se toca nada.
 */
final class ReglasDeOrden {

    private ReglasDeOrden() {
    }

    static <T> Map<Long, T> exigirLaListaCompleta(List<Long> ids, List<T> hijos, Function<T, Long> id, String queja) {
        Map<Long, T> porId = new LinkedHashMap<>();
        hijos.forEach(hijo -> porId.put(id.apply(hijo), hijo));
        if (ids.size() != hijos.size() || !new HashSet<>(ids).equals(porId.keySet())) {
            throw new ReglaNegocioException(queja);
        }
        return porId;
    }
}
