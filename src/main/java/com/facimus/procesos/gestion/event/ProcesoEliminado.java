package com.facimus.procesos.gestion.event;

/**
 * Se publica al dar de baja un proceso, dentro de su transaccion. Lo escucha el modulo de modelado para dar de baja el
 * modelo del proceso: asi gestion no necesita conocer los elementos BPMN.
 */
public record ProcesoEliminado(Long empresaId, Long procesoId) {
}
