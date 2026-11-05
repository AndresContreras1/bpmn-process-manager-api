package com.facimus.procesos.common;

/**
 * Los nombres de las caches de lo publicado (D19). Los usan el modulo que cachea y config, que las arma: en common
 * ninguno de los dos depende del otro.
 */
public final class Caches {

    /** El grafo ya masticado de una version publicada: nodos, arcos, condiciones compiladas y alcances. */
    public static final String GRAFOS = "grafos-de-version";

    /** El JSON del diagrama tal como se publico, que es lo que lee quien solo puede ver lo publicado. */
    public static final String DEFINICIONES = "definiciones-de-version";

    private Caches() {
    }
}
