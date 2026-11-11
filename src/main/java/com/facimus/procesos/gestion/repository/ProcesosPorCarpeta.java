package com.facimus.procesos.gestion.repository;

/** PR 59: cuantos procesos activos tiene una carpeta. */
public record ProcesosPorCarpeta(Long carpetaId, long procesos) {
}
