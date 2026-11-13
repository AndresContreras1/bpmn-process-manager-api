package com.facimus.procesos.ejecucion.repository;

import java.util.Set;

/**
 * PR 71: los procesos que quien pregunta puede ver (PR 60), en la forma en que los filtra una consulta agrupada. Sin
 * nada oculto, {@code todos}. Si no, se ve un proceso que no esta en una carpeta oculta, o que tiene una concesion
 * propia que lo deja ver, y que no esta oculto el mismo. Un conjunto vacio no se le pasa a la base: un {@code in ()}
 * vacio no es SQL, y un id que no existe no cambia lo que cuenta.
 */
public record ProcesosVisibles(boolean todos, Set<Long> carpetasOcultas, Set<Long> procesosOcultos,
        Set<Long> procesosVisibles) {

    /** Ningun id es negativo: es el que va en lugar de un conjunto vacio. */
    private static final Set<Long> NINGUNO = Set.of(-1L);

    public static final ProcesosVisibles TODOS = new ProcesosVisibles(true, Set.of(), Set.of(), Set.of());

    public ProcesosVisibles {
        carpetasOcultas = noVacio(carpetasOcultas);
        procesosOcultos = noVacio(procesosOcultos);
        procesosVisibles = noVacio(procesosVisibles);
    }

    private static Set<Long> noVacio(Set<Long> ids) {
        return ids.isEmpty() ? NINGUNO : Set.copyOf(ids);
    }
}
