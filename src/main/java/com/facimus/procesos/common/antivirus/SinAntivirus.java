package com.facimus.procesos.common.antivirus;

import java.io.InputStream;

/**
 * PR 61: cuando no hay antivirus configurado. No finge revisar: dice que no esta, y quien lo usa decide que hacer
 * con un documento que nadie reviso.
 */
public class SinAntivirus implements Antivirus {

    @Override
    public boolean disponible() {
        return false;
    }

    @Override
    public ResultadoDelAntivirus revisar(InputStream contenido) {
        throw new IllegalStateException("No hay antivirus configurado (ANTIVIRUS_HOST).");
    }
}
