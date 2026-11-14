package com.facimus.procesos.modelado.service.impl;

import org.springframework.stereotype.Component;

import com.facimus.procesos.common.trabajos.ManejadorDeTrabajo;
import com.facimus.procesos.common.trabajos.Trabajo;
import com.facimus.procesos.modelado.service.TextoDeDocumentosService;

import lombok.RequiredArgsConstructor;

/**
 * PR 65: el trabajo de la cola que lee el texto de cada version disponible. Es idempotente: una version que ya tiene
 * su texto, o que dejo de estar disponible, no se vuelve a leer.
 */
@Component
@RequiredArgsConstructor
public class LecturaDeDocumentos implements ManejadorDeTrabajo {

    private final TextoDeDocumentosService textos;

    @Override
    public String tipo() {
        return TextoDeDocumentosServiceImpl.TRABAJO_DE_LECTURA;
    }

    @Override
    public void ejecutar(Trabajo trabajo) {
        textos.leer(trabajo.empresaId(), Long.valueOf(trabajo.datos()));
    }
}
