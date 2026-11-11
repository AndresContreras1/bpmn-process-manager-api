package com.facimus.procesos.modelado.service;

import java.io.InputStream;

import com.facimus.procesos.modelado.dto.response.ComparacionResponse;

/** PR 57: que cambia entre dos estados de un proceso. */
public interface ComparacionService {

    /**
     * @param de un numero de version, {@code vigente} o {@code borrador}
     * @param a lo mismo
     */
    ComparacionResponse comparar(Long empresaId, Long procesoId, String de, String a);

    /** Que le haria este archivo al borrador, sin guardarlo: lo que ve quien recibe un conflicto al guardar. */
    ComparacionResponse compararArchivo(Long empresaId, Long usuarioId, Long procesoId, InputStream archivo);
}
