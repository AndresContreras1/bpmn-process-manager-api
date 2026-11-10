package com.facimus.procesos.gestion.event;

/**
 * PR 52: se publica cada vez que el historial anota un cambio de un proceso, dentro de su transaccion. Lo escucha el
 * modulo de modelado para recalcular la geometria BPMN DI del diagrama: cada cambio del modelo deja su linea en el
 * historial, asi que ninguno se escapa.
 */
public record ProcesoModificado(Long empresaId, Long procesoId) {
}
