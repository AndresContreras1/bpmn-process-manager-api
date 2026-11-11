package com.facimus.procesos.gestion.service;

import java.util.Set;

/**
 * PR 60: lo que una persona no puede ver, resuelto de una vez para filtrar una lista en su misma consulta. Un proceso con
 * una concesion propia no depende de su carpeta: por eso hay una lista de los que se ven aunque la carpeta no.
 */
public record Visibilidad(boolean todo, Set<Long> carpetasOcultas, Set<Long> procesosOcultos,
        Set<Long> procesosVisibles) {

    public static final Visibilidad TODO = new Visibilidad(true, Set.of(), Set.of(), Set.of());

    public Visibilidad {
        carpetasOcultas = Set.copyOf(carpetasOcultas);
        procesosOcultos = Set.copyOf(procesosOcultos);
        procesosVisibles = Set.copyOf(procesosVisibles);
    }
}
