package com.facimus.procesos.gestion.service;

import java.time.LocalDate;
import java.util.List;

import com.facimus.procesos.gestion.model.Clasificacion;

/** PR 59: donde esta archivado un proceso y quien responde por el. PR 64: y cada cuanto se revisa; vacio, lo mismo. */
public record DatosDeFicha(Long carpetaId, Long duenoId, LocalDate proximaRevision, Clasificacion clasificacion,
        List<String> etiquetas, Long version, Integer mesesEntreRevisiones) {

    /** La de antes del PR 64: deja los meses entre revisiones como esten. */
    public DatosDeFicha(Long carpetaId, Long duenoId, LocalDate proximaRevision, Clasificacion clasificacion,
            List<String> etiquetas, Long version) {
        this(carpetaId, duenoId, proximaRevision, clasificacion, etiquetas, version, null);
    }
}
