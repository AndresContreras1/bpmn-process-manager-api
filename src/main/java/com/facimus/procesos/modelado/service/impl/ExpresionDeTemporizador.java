package com.facimus.procesos.modelado.service.impl;

import java.util.Optional;

import com.facimus.procesos.common.tiempo.ExpresionDeTiempo;
import com.facimus.procesos.common.tiempo.ExpresionMalEscrita;
import com.facimus.procesos.modelado.model.TipoTemporizador;

/**
 * PR 55: si un temporizador dice cuando salta, en ISO 8601 como lo pide BPMN: una duracion (P3D, PT2H), una fecha y
 * hora (2026-12-24T09:00:00Z) o un ciclo de repeticiones (R3/PT1H, R/2026-01-01T08:00:00Z/P1D). PR 68: tambien una
 * duracion habil (P2D son dos dias de jornada) y un ciclo en cron; la gramatica es la que usa el motor para contar.
 */
final class ExpresionDeTemporizador {

    private ExpresionDeTemporizador() {
    }

    /** Lo que esta mal, o vacio si se entiende. */
    static Optional<String> problema(TipoTemporizador tipo, String expresion) {
        if (expresion == null || expresion.isBlank()) {
            return Optional.of("Este temporizador no dice cuando salta.");
        }
        if (tipo == null) {
            return Optional.of("Este temporizador no dice si espera una duracion, una fecha o un ciclo.");
        }
        try {
            switch (tipo) {
                case DURACION -> ExpresionDeTiempo.duracion(expresion);
                case DURACION_HABIL -> ExpresionDeTiempo.duracionHabil(expresion);
                case FECHA -> ExpresionDeTiempo.fecha(expresion);
                case CICLO -> ExpresionDeTiempo.ciclo(expresion);
            }
            return Optional.empty();
        } catch (ExpresionMalEscrita mal) {
            return Optional.of(mal.getMessage());
        }
    }
}
