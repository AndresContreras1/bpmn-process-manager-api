package com.facimus.procesos.gestion.service;

import java.time.LocalDate;

import com.facimus.procesos.gestion.model.Clasificacion;
import com.facimus.procesos.gestion.model.EstadoProceso;

/** PR 59: lo que la lista de procesos puede filtrar; lo vacio no filtra. Una carpeta incluye las de adentro. */
public record FiltrosDeProceso(String nombre, EstadoProceso estado, String categoria, boolean incluirInactivos,
        Long carpetaId, String etiqueta, Long duenoId, Clasificacion clasificacion, LocalDate revisionAntesDe,
        Actor actor) {

    /** Sin un actor no se recorta nada: es quien pide en nombre del sistema. */
    public FiltrosDeProceso(String nombre, EstadoProceso estado, String categoria, boolean incluirInactivos,
            Long carpetaId, String etiqueta, Long duenoId, Clasificacion clasificacion, LocalDate revisionAntesDe) {
        this(nombre, estado, categoria, incluirInactivos, carpetaId, etiqueta, duenoId, clasificacion, revisionAntesDe,
                null);
    }
}
