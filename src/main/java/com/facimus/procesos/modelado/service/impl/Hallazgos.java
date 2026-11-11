package com.facimus.procesos.modelado.service.impl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.facimus.procesos.modelado.dto.response.HallazgoDiagnosticoResponse;
import com.facimus.procesos.modelado.model.CodigoDeDiagnostico;

/**
 * Lo que va encontrando el diagnostico. Sale ordenado por gravedad, luego por codigo y luego por elemento, asi que
 * el mismo diagrama da siempre la misma lista y dos diagnosticos se pueden comparar.
 */
final class Hallazgos {

    private static final Comparator<HallazgoDiagnosticoResponse> ORDEN = Comparator
            .comparing(HallazgoDiagnosticoResponse::severidad)
            .thenComparing(HallazgoDiagnosticoResponse::codigo)
            .thenComparing(HallazgoDiagnosticoResponse::elementoId,
                    Comparator.nullsFirst(Comparator.naturalOrder()));

    private final List<HallazgoDiagnosticoResponse> lista = new ArrayList<>();
    private final boolean soloEstructural;

    Hallazgos() {
        this(false);
    }

    /** PR 55: los de un proceso documental, que solo cuentan lo estructural. */
    Hallazgos(boolean soloEstructural) {
        this.soloEstructural = soloEstructural;
    }

    /** Un hallazgo sobre un elemento concreto del diagrama. */
    void anotar(CodigoDeDiagnostico codigo, String elemento, Long elementoId, String problema, String sugerencia) {
        if (soloEstructural && !codigo.esEstructural()) {
            return;
        }
        lista.add(new HallazgoDiagnosticoResponse(codigo.codigo(), codigo.severidad(), elemento, elementoId, problema,
                sugerencia));
    }

    /** Un hallazgo sobre el proceso entero, que no senala a ningun elemento. */
    void anotar(CodigoDeDiagnostico codigo, String problema, String sugerencia) {
        anotar(codigo, null, null, problema, sugerencia);
    }

    List<HallazgoDiagnosticoResponse> ordenados() {
        return lista.stream().sorted(ORDEN).toList();
    }
}
