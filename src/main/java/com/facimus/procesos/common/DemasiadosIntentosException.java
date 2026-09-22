package com.facimus.procesos.common;

import java.time.Duration;

/** 429: demasiados intentos fallidos. Dice cuanto hay que esperar, para la cabecera Retry-After. */
public class DemasiadosIntentosException extends RuntimeException {

    private final long segundosDeEspera;

    public DemasiadosIntentosException(Duration espera) {
        this(Math.max(1, (espera.toMillis() + 999) / 1000));
    }

    private DemasiadosIntentosException(long segundosDeEspera) {
        super("Demasiados intentos fallidos de inicio de sesión. Intenta de nuevo en " + minutos(segundosDeEspera)
                + ".");
        this.segundosDeEspera = segundosDeEspera;
    }

    /** Segundos enteros, redondeados hacia arriba: nunca invita a reintentar antes de tiempo. */
    public long getSegundosDeEspera() {
        return segundosDeEspera;
    }

    private static String minutos(long segundos) {
        long minutos = (segundos + 59) / 60;
        return minutos == 1 ? "1 minuto" : minutos + " minutos";
    }
}
