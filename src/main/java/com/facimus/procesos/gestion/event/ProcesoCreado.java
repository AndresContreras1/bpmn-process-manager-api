package com.facimus.procesos.gestion.event;

/**
 * Se publica al crear un proceso, dentro de su transaccion. Lo escucha el modulo de modelado para crear el pool
 * de la empresa duena: asi gestion no necesita conocer los pools.
 */
public record ProcesoCreado(Long empresaId, Long procesoId) {
}
