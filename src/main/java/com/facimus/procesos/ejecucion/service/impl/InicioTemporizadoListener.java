package com.facimus.procesos.ejecucion.service.impl;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.facimus.procesos.ejecucion.service.TemporizadorService;
import com.facimus.procesos.gestion.event.ProcesoEliminado;
import com.facimus.procesos.gestion.event.ProcesoPublicado;
import com.facimus.procesos.gestion.event.VersionRetirada;

import lombok.RequiredArgsConstructor;

/**
 * PR 68: los inicios temporizados siguen a la version vigente. Publicar arma los de la nueva y cancela los de la
 * anterior; retirar la vigente vuelve a armar los de la que queda; dar de baja el proceso los cancela. Corre en la
 * misma transaccion que lo provoca: si la publicacion se deshace, sus temporizadores tambien.
 */
@Component
@RequiredArgsConstructor
public class InicioTemporizadoListener {

    private final TemporizadorService temporizadorService;

    @EventListener
    public void alPublicarse(ProcesoPublicado evento) {
        temporizadorService.sincronizarInicios(evento.empresaId(), evento.procesoId());
    }

    @EventListener
    public void alRetirarse(VersionRetirada evento) {
        temporizadorService.sincronizarInicios(evento.empresaId(), evento.procesoId());
    }

    @EventListener
    public void alEliminarse(ProcesoEliminado evento) {
        temporizadorService.cancelarInicios(evento.empresaId(), evento.procesoId());
    }
}
