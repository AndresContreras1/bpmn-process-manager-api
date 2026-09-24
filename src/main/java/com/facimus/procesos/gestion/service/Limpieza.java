package com.facimus.procesos.gestion.service;

/** Lo que se llevo una pasada de la limpieza, tabla por tabla. */
public record Limpieza(int refreshTokens, int sesiones, int clavesIdempotencia) {

    public int total() {
        return refreshTokens + sesiones + clavesIdempotencia;
    }
}
