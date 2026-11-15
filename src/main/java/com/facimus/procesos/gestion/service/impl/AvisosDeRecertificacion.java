package com.facimus.procesos.gestion.service.impl;

import java.time.LocalDate;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import com.facimus.procesos.common.trabajos.ManejadorDeTrabajo;
import com.facimus.procesos.common.trabajos.Trabajo;
import com.facimus.procesos.gestion.service.RecertificacionService;

import lombok.RequiredArgsConstructor;

/**
 * PR 64: el trabajo de la cola que avisa a los duenos de una tienda. El dia va en el trabajo, asi que un reintento al
 * dia siguiente avisa lo de su dia. El servicio se pide al correr, porque el servicio encola y la cola junta sus
 * manejadores al crearse.
 */
@Component
@RequiredArgsConstructor
public class AvisosDeRecertificacion implements ManejadorDeTrabajo {

    private final ObjectProvider<RecertificacionService> recertificacion;

    @Override
    public String tipo() {
        return RecertificacionServiceImpl.TRABAJO;
    }

    @Override
    public void ejecutar(Trabajo trabajo) {
        recertificacion.getObject().avisar(trabajo.empresaId(), LocalDate.parse(trabajo.datos()));
    }
}
