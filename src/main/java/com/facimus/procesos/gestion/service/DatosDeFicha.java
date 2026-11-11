package com.facimus.procesos.gestion.service;

import java.time.LocalDate;
import java.util.List;

import com.facimus.procesos.gestion.model.Clasificacion;

/** PR 59: donde esta archivado un proceso y quien responde por el. */
public record DatosDeFicha(Long carpetaId, Long duenoId, LocalDate proximaRevision, Clasificacion clasificacion,
        List<String> etiquetas, Long version) {
}
