package com.facimus.procesos.gestion.service;

/**
 * D20: las tres tablas tecnicas del sistema (sesiones, refresh tokens y claves de idempotencia) solo crecen. Cada
 * inicio de sesion deja una fila, cada renovacion otra, y cada peticion con {@code Idempotency-Key} una mas; nadie
 * las vuelve a leer una vez que caducan. Esto las barre.
 */
public interface LimpiezaService {

    /** Una pasada completa. Devuelve cuanto se llevo, que es lo unico que se registra. */
    Limpieza limpiar();
}
