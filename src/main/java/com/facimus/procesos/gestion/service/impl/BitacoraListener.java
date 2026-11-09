package com.facimus.procesos.gestion.service.impl;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import com.facimus.procesos.common.auditoria.SucesoAuditable;
import com.facimus.procesos.gestion.service.BitacoraService;

/**
 * PR 31: la bitacora se alimenta de los sucesos que publican los servicios; ninguno la escribe a mano. Corre
 * despues del commit de lo que paso, en su propia transaccion, y el suceso espera en el outbox hasta que queda
 * anotado: un reinicio a mitad de camino no lo pierde.
 */
@Component
public class BitacoraListener {

    private final BitacoraService bitacoraService;

    public BitacoraListener(BitacoraService bitacoraService) {
        this.bitacoraService = bitacoraService;
    }

    @ApplicationModuleListener
    public void anotar(SucesoAuditable suceso) {
        bitacoraService.anotar(suceso);
    }
}
