package com.facimus.procesos.gestion.service.impl;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import com.facimus.procesos.common.trabajos.ManejadorDeTrabajo;
import com.facimus.procesos.common.trabajos.Trabajo;
import com.facimus.procesos.gestion.service.ExportacionService;

import lombok.RequiredArgsConstructor;

/**
 * PR 66: el trabajo de la cola que arma una exportacion. Es idempotente: una que ya no esta pendiente no se vuelve a
 * armar. El servicio se pide al correr, porque el servicio encola y la cola junta sus manejadores al crearse.
 */
@Component
@RequiredArgsConstructor
public class GeneracionDeExportaciones implements ManejadorDeTrabajo {

    private final ObjectProvider<ExportacionService> exportaciones;

    @Override
    public String tipo() {
        return ExportacionServiceImpl.TRABAJO_DE_GENERACION;
    }

    @Override
    public void ejecutar(Trabajo trabajo) {
        exportaciones.getObject().generar(trabajo.empresaId(), Long.valueOf(trabajo.datos()));
    }
}
