package com.facimus.procesos.common.antivirus;

/**
 * PR 61: lo que dijo el antivirus de un archivo.
 *
 * @param firma el nombre de lo que encontro, o {@code null} si esta limpio
 */
public record ResultadoDelAntivirus(boolean limpio, String firma) {

    public static ResultadoDelAntivirus sinHallazgos() {
        return new ResultadoDelAntivirus(true, null);
    }

    public static ResultadoDelAntivirus infectado(String firma) {
        return new ResultadoDelAntivirus(false, firma);
    }
}
