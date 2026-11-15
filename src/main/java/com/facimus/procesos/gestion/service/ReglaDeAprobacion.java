package com.facimus.procesos.gestion.service;

import java.util.List;

/**
 * PR 64: lo que pide la politica a una solicitud concreta: cuantas aprobaciones, y de quienes. Los aprobadores ya son
 * los que pueden serlo de esa solicitud: activos, con permiso de aprobar el proceso y sin quien la pide.
 */
public record ReglaDeAprobacion(int necesarias, List<Long> aprobadores) {

    public ReglaDeAprobacion {
        aprobadores = List.copyOf(aprobadores);
    }
}
