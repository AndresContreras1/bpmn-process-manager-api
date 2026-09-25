package com.facimus.procesos.ejecucion.service.impl;

import java.util.Optional;

import com.facimus.procesos.common.condiciones.Condicion;

/**
 * Una salida de un nodo en la version publicada, con su condicion ya compilada. Compilar al construir el grafo y no
 * al decidir evita rehacer el analisis por cada caso que pasa por el mismo gateway.
 */
record ArcoDeLaVersion(Long id, Long origenId, Long destinoId, String etiqueta, boolean porDefecto, int orden,
        Condicion condicion) {

    /**
     * Como se nombra en la bitacora. Van la etiqueta y la condicion juntas cuando hay las dos: quien lee la linea
     * de tiempo quiere ver por donde se fue el caso y tambien por que, sin abrir el diagrama al lado.
     */
    String comoSeLlama() {
        String condicionTexto = Optional.ofNullable(condicion).map(Condicion::texto).orElse("");
        if (porDefecto) {
            return tieneEtiqueta() ? etiqueta + " (por defecto)" : "la salida por defecto";
        }
        if (!tieneEtiqueta()) {
            return condicionTexto.isEmpty() ? "la salida sin condicion" : condicionTexto;
        }
        return condicionTexto.isEmpty() ? etiqueta : etiqueta + " (" + condicionTexto + ")";
    }

    private boolean tieneEtiqueta() {
        return etiqueta != null && !etiqueta.isBlank();
    }
}
