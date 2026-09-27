package com.facimus.procesos.ejecucion.service.impl;

import org.springframework.stereotype.Component;

import com.facimus.procesos.gestion.model.VersionProceso;

import lombok.RequiredArgsConstructor;

/**
 * De donde salen los grafos: de la instantanea que guardo la publicacion, que es el mismo JSON que devuelve
 * GET /procesos/{id}/diagrama. Pasar por aqui y no armarlo en cada service deja un solo sitio donde esta la cache
 * (D19), y un solo sitio del que sale su clave.
 *
 * <p>La version casi siempre llega como proxy perezoso, porque viene de {@code caso.getVersionProceso()}. Pedirle el
 * id no lo despierta; pedirle la definicion si. Por eso la tienda se pasa aparte -el service ya la conoce, y
 * sacarla de la version costaria justo la consulta que se quiere evitar- y el JSON va como proveedor.
 */
@Component
@RequiredArgsConstructor
class GrafosDeVersion {

    private final CacheDeGrafos cache;

    GrafoDeVersion del(Long empresaId, VersionProceso version) {
        return cache.grafo(empresaId, version.getId(), version::getDefinicion);
    }
}
