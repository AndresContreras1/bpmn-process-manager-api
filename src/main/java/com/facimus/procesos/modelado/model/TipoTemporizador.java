package com.facimus.procesos.modelado.model;

/** PR 55: como se dice cuando salta un temporizador, en ISO 8601, como lo escribe BPMN. */
public enum TipoTemporizador {
    /** Al pasar un tiempo de calendario: {@code PT2H}, {@code P3D}. */
    DURACION,
    /**
     * PR 68: al pasar un tiempo habil, contado con el calendario de la tienda: {@code P2D} son dos dias de jornada.
     * En BPMN es un {@code timeDuration} con {@code bpm:habil="true"}.
     */
    DURACION_HABIL,
    /** En una fecha: {@code 2026-12-24T09:00:00Z}. */
    FECHA,
    /** Cada cierto tiempo, unas veces: {@code R3/PT1H}; PR 68: o en cron, {@code 0 0 9 * * MON-FRI}. */
    CICLO;

    /** PR 68: lo que puede volver a saltar: un ciclo. Lo demas salta una vez. */
    public boolean seRepite() {
        return this == CICLO;
    }
}
