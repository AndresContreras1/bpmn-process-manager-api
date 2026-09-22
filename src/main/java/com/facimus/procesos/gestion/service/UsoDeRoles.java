package com.facimus.procesos.gestion.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Cuanto se usa un rol de proceso. Los roles son de gestion pero quienes los usan son las lanes, que viven en
 * modelado: gestion declara lo que necesita saber y modelado lo implementa, asi la dependencia va en un solo sentido.
 */
public interface UsoDeRoles {

    /** Cuantos procesos activos de la empresa tienen al menos una lane del rol. */
    long contarProcesos(Long empresaId, Long rolId);

    /** Nombres de esos procesos, para explicar por que el rol no se puede eliminar. */
    List<String> procesosQueLoUsan(Long empresaId, Long rolId);

    /** El conteo de varios roles en una consulta, para un listado: un rol que no aparece no lo usa ningun proceso. */
    Map<Long, Long> contarProcesosPorRol(Long empresaId, Collection<Long> rolIds);
}
