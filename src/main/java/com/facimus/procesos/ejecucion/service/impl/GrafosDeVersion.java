package com.facimus.procesos.ejecucion.service.impl;

import org.springframework.stereotype.Component;

import com.facimus.procesos.gestion.model.VersionProceso;
import com.facimus.procesos.modelado.dto.response.DiagramaResponse;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.json.JsonMapper;

/**
 * De donde salen los grafos: de la instantanea que guardo la publicacion, que es el mismo JSON que devuelve
 * GET /procesos/{id}/diagrama. Leerlo aqui y no en cada service deja un solo sitio donde poner la cache el dia que
 * el PR de carga diga que hace falta (D19); mientras tanto, cada caso arma el suyo, que es exacto por definicion.
 */
@Component
@RequiredArgsConstructor
class GrafosDeVersion {

    private final JsonMapper json;

    GrafoDeVersion del(VersionProceso version) {
        return GrafoDeVersion.de(json.readValue(version.getDefinicion(), DiagramaResponse.class));
    }
}
