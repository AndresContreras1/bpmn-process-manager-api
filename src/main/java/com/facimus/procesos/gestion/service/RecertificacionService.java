package com.facimus.procesos.gestion.service;

import java.time.LocalDate;

/**
 * PR 64: los avisos de la recertificacion. Cuando llega la fecha de revision de un proceso se le avisa a su dueno, y
 * una vez mas cuando vence sin que nadie confirme que sigue vigente; cada fecha, una vez cada aviso.
 */
public interface RecertificacionService {

    /** Corre como sistema, una vez al dia: deja en la cola un trabajo por tienda con algun aviso pendiente. */
    int encolarAvisos(LocalDate hoy);

    /** El trabajo de una tienda: pide los avisos que le tocan ese dia y los anota. Devuelve cuantos pidio. */
    int avisar(Long empresaId, LocalDate hoy);
}
