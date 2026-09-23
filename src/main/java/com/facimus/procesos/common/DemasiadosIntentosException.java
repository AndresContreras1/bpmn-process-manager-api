package com.facimus.procesos.common;

import java.time.Duration;

/** 429: demasiados intentos fallidos. Dice cuanto hay que esperar, para la cabecera Retry-After. */
public class DemasiadosIntentosException extends RuntimeException {

    private final long segundosDeEspera;

    public DemasiadosIntentosException(Duration espera) {
        this("Demasiados intentos fallidos de inicio de sesión. Intenta de nuevo en "
                + minutos(segundos(espera)) + ".", espera);
    }

    /** El mismo 429 para otra cosa que se limita, con su propio mensaje. */
    public DemasiadosIntentosException(String mensaje, Duration espera) {
        super(mensaje);
        this.segundosDeEspera = segundos(espera);
    }

    /** Segundos enteros, redondeados hacia arriba: nunca invita a reintentar antes de tiempo. */
    private static long segundos(Duration espera) {
        return Math.max(1, (espera.toMillis() + 999) / 1000);
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
