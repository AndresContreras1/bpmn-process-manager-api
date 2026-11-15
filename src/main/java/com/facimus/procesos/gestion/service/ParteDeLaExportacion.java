package com.facimus.procesos.gestion.service;

/**
 * PR 66: lo que un modulo pone en la exportacion completa de una tienda. Gestion arma el ZIP y su manifiesto, pero
 * cada modulo conoce sus tablas: gestion declara este puerto y cada uno lo implementa con lo suyo, como con
 * {@link InstantaneaDelModelo}. Corre en la cola, con la tienda fijada en el contexto: la base no le deja leer filas de
 * otra.
 */
public interface ParteDeLaExportacion {

    /** Escribe en el paquete lo de esa tienda que le toca. Lanzar es que la exportacion entera falle. */
    void escribir(Long empresaId, Paquete paquete);
}
