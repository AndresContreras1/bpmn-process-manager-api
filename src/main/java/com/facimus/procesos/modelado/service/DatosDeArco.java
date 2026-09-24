package com.facimus.procesos.modelado.service;

/**
 * Lo que define un arco: por donde va y como lo toma el gateway del que sale. Al editar, un extremo vacio deja el
 * que ya tenia, porque mover una flecha y renombrarla son dos gestos distintos del editor.
 */
public record DatosDeArco(Long origenId, Long destinoId, String etiqueta, String condicion, boolean porDefecto,
        int orden) {

    /** Un arco corriente: une dos nodos, sin etiqueta ni condicion. */
    public static DatosDeArco entre(Long origenId, Long destinoId) {
        return new DatosDeArco(origenId, destinoId, null, null, false, 0);
    }
}
