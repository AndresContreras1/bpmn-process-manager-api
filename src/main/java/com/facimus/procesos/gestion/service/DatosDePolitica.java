package com.facimus.procesos.gestion.service;

import java.util.List;

import com.facimus.procesos.gestion.model.ModoDePublicacion;

/**
 * PR 64: la politica de publicacion que pide el administrador. Las aprobaciones que faltan valen 1; los aprobadores
 * salen de la lista o del rol, de uno de los dos.
 */
public record DatosDePolitica(ModoDePublicacion modo, Integer aprobacionesNecesarias, Long rolProcesoId,
        List<Long> aprobadores, Long version) {

    public DatosDePolitica {
        aprobadores = aprobadores == null ? List.of() : List.copyOf(aprobadores);
    }
}
