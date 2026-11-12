package com.facimus.procesos.modelado.service.impl;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import com.facimus.procesos.common.trabajos.ManejadorDeTrabajo;
import com.facimus.procesos.common.trabajos.Trabajo;
import com.facimus.procesos.modelado.service.ManualService;

import lombok.RequiredArgsConstructor;

/**
 * PR 62: el trabajo de la cola que arma un manual. Es idempotente: un manual que ya no esta pendiente no se vuelve a
 * armar. El servicio se pide al correr, porque el servicio encola y la cola junta sus manejadores al crearse.
 */
@Component
@RequiredArgsConstructor
public class GeneracionDeManuales implements ManejadorDeTrabajo {

    private final ObjectProvider<ManualService> manuales;

    @Override
    public String tipo() {
        return ManualServiceImpl.TRABAJO_DE_GENERACION;
    }

    @Override
    public void ejecutar(Trabajo trabajo) {
        manuales.getObject().generar(trabajo.empresaId(), Long.valueOf(trabajo.datos()));
    }
}
