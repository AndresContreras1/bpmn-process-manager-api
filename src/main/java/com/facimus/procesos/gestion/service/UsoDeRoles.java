package com.facimus.procesos.gestion.service;

import java.util.List;

/**
 * Cuanto se usa un rol de proceso. Los roles son de gestion pero quienes los usan son las lanes, que viven en
 * modelado: gestion declara lo que necesita saber y modelado lo implementa, asi la dependencia va en un solo sentido.
 */
public interface UsoDeRoles {

    /** Cuantas veces se usa el rol en los procesos de la empresa. */
    long contarUsos(Long empresaId, Long rolId);

    /** Nombres de los procesos que usan el rol, para explicar por que no se puede eliminar. */
    List<String> procesosQueLoUsan(Long empresaId, Long rolId);
}
