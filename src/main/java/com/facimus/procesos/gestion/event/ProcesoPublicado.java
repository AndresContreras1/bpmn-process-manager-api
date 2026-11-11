package com.facimus.procesos.gestion.event;

/** PR 58: se publico una version nueva de un proceso; quien lo este mirando quiere saberlo. */
public record ProcesoPublicado(Long empresaId, Long procesoId, int numero, String autor) {
}
