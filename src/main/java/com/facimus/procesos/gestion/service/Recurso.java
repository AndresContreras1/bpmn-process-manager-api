package com.facimus.procesos.gestion.service;

/** PR 60: sobre que se pide algo. */
public record Recurso(Tipo tipo, Long id) {

    public enum Tipo {
        CARPETA,
        PROCESO
    }

    public static Recurso proceso(Long id) {
        return new Recurso(Tipo.PROCESO, id);
    }

    public static Recurso carpeta(Long id) {
        return new Recurso(Tipo.CARPETA, id);
    }
}
