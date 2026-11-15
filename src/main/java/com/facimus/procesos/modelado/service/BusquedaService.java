package com.facimus.procesos.modelado.service;

import org.springframework.data.domain.Pageable;

import com.facimus.procesos.common.api.PageResponse;
import com.facimus.procesos.gestion.service.Actor;
import com.facimus.procesos.modelado.dto.response.ResultadoDeBusquedaResponse;
import com.facimus.procesos.modelado.model.TipoDeResultado;

/**
 * PR 65: la busqueda de texto completo en lo que una persona puede ver de su tienda: procesos, elementos del borrador y
 * de la version publicada, documentos y comentarios.
 */
public interface BusquedaService {

    /** Largo maximo de lo que se busca. */
    int LARGO_MAXIMO = 200;

    /**
     * Lo que encuentra el texto, con la sintaxis de un buscador web (comillas, {@code -} y {@code or}), lo mas
     * relevante primero. Sin tipo, de todos. Un texto vacio, o solo de palabras que no cuentan, no encuentra nada.
     */
    PageResponse<ResultadoDeBusquedaResponse> buscar(Actor actor, String texto, TipoDeResultado tipo,
            Pageable pagina);
}
