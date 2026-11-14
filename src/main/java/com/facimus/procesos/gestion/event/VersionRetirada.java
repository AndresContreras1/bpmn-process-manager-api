package com.facimus.procesos.gestion.event;

/**
 * PR 68: se retiro una version de un proceso. Si era la vigente, el proceso vuelve a la anterior que siga en pie, y lo
 * que dependa de cual es la vigente, como sus inicios temporizados, tiene que saberlo.
 */
public record VersionRetirada(Long empresaId, Long procesoId, int numero) {
}
