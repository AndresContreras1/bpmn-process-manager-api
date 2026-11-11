package com.facimus.procesos.modelado.model;

/** PR 55: lo que se dibuja para explicar el proceso, sin ser parte de su flujo. */
public enum TipoArtefacto {
    /** Un texto pegado al diagrama. */
    ANOTACION,
    /** Un recuadro que junta elementos con algo en comun. */
    GRUPO,
    /** Un documento o dato que el proceso usa o produce. */
    OBJETO_DATOS,
    /** Donde se guardan los datos: una base, un sistema. */
    ALMACEN_DATOS
}
