package com.facimus.procesos.modelado.service.impl;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import com.facimus.procesos.common.trabajos.ManejadorDeTrabajo;
import com.facimus.procesos.common.trabajos.Trabajo;
import com.facimus.procesos.modelado.service.DocumentoService;

import lombok.RequiredArgsConstructor;

/**
 * PR 61: el trabajo de la cola que revisa cada archivo subido. Es idempotente: una version que ya no esta pendiente
 * no se vuelve a revisar. Si el antivirus no contesta, el trabajo falla y la cola lo vuelve a intentar mas tarde; el
 * documento sigue pendiente mientras tanto.
 * <p>El servicio se pide al correr: la cola junta sus manejadores al crearse, y el servicio encola, asi que pedirlo
 * antes cerraria un circulo.
 */
@Component
@RequiredArgsConstructor
public class RevisionDeDocumentos implements ManejadorDeTrabajo {

    private final ObjectProvider<DocumentoService> documentos;

    @Override
    public String tipo() {
        return DocumentoServiceImpl.REVISAR;
    }

    @Override
    public void ejecutar(Trabajo trabajo) {
        documentos.getObject().revisar(trabajo.empresaId(), Long.valueOf(trabajo.datos()));
    }
}
