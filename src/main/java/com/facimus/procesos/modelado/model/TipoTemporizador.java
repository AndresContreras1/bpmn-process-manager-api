package com.facimus.procesos.modelado.model;

/** PR 55: como se dice cuando salta un temporizador, en ISO 8601, como lo escribe BPMN. */
public enum TipoTemporizador {
    /** Al pasar un tiempo: {@code PT2H}, {@code P3D}. */
    DURACION,
    /** En una fecha: {@code 2026-12-24T09:00:00Z}. */
    FECHA,
    /** Cada cierto tiempo, unas veces: {@code R3/PT1H}. */
    CICLO
}
