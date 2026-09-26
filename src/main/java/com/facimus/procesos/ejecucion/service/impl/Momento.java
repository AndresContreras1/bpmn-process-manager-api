package com.facimus.procesos.ejecucion.service.impl;

/**
 * Cuando trabaja el motor y por quien: el tick del reloj de la tienda y el usuario que lo pidio, si lo pidio
 * alguien. Todo lo que ocurre en una misma operacion ocurre en el mismo tick, asi que el reloj se lee una vez al
 * empezar y viaja con ella, en vez de preguntarse otra vez por cada linea que se escribe.
 *
 * <p>El autor no se pone en todas las lineas: lo que el motor hace por su cuenta no lo hizo nadie, y una bitacora
 * que se lo atribuyera a quien completo la tarea anterior estaria contando algo falso.
 */
record Momento(int tick, Long autorId) {

    /** Lo que mueve el reloj o un mensaje que llega: pasa en un tick, pero no lo pidio ninguna persona. */
    static Momento en(int tick) {
        return new Momento(tick, null);
    }
}
