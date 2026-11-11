package com.facimus.procesos.modelado.service;

import com.facimus.procesos.modelado.dto.response.ActividadResponse;

/** PR 56: que subproceso contiene a un nodo, y que proceso corre una actividad de llamada. */
public interface SubprocesoService {

    /** Mete un nodo en un subproceso, o lo saca al flujo principal con {@code padreId} vacio. */
    void mover(Long empresaId, Long usuarioId, Long nodoId, Long padreId);

    /** El proceso que corre una actividad de llamada; vacio, ninguno. */
    ActividadResponse llamarA(Long empresaId, Long usuarioId, Long actividadId, Long procesoId, Long version);
}
