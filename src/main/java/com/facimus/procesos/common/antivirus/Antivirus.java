package com.facimus.procesos.common.antivirus;

import java.io.InputStream;

/**
 * PR 61: el antivirus que revisa cada documento antes de que alguien lo pueda bajar. Revisar es lento, asi que corre
 * en la cola: el documento espera {@code PENDIENTE} hasta que el antivirus contesta.
 */
public interface Antivirus {

    /** Si hay un antivirus de verdad detras; sin el, nada se revisa y el documento lo dice. */
    boolean disponible();

    /**
     * Revisa el contenido. Un fallo del antivirus (caido, que no contesta a tiempo) es una excepcion: la cola lo vuelve a
     * intentar, y el documento no queda disponible por un error.
     */
    ResultadoDelAntivirus revisar(InputStream contenido);
}
