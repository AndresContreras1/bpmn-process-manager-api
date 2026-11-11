package com.facimus.procesos.modelado.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.facimus.procesos.modelado.model.TipoTemporizador;

class ExpresionDeTemporizadorTest {

    @ParameterizedTest
    @CsvSource({"DURACION,PT2H", "DURACION,P3D", "DURACION,P1DT2H", "DURACION,P1M", "DURACION,P1Y2M3D",
            "FECHA,2026-12-24T09:00:00Z", "FECHA,2026-12-24T09:00:00", "FECHA,2026-12-24T09:00:00-05:00",
            "CICLO,R3/PT1H", "CICLO,R/P1D", "CICLO,R/2026-01-01T08:00:00Z/P1D", "CICLO,R2/PT1H/2026-01-01T08:00:00Z"})
    @DisplayName("Lo que ISO 8601 entiende no tiene problema")
    void validas(TipoTemporizador tipo, String expresion) {
        assertThat(ExpresionDeTemporizador.problema(tipo, expresion)).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"DURACION,two hours", "DURACION,2026-12-24T09:00:00Z", "FECHA,PT2H", "FECHA,2026-12-24", "CICLO,PT1H",
            "CICLO,R3", "CICLO,Rx/PT1H", "CICLO,R3/nada", "CICLO,R/2026-01-01T08:00:00Z",
            "CICLO,R/PT1H/PT1H/PT1H"})
    @DisplayName("Lo que no, dice que no lo es")
    void invalidas(TipoTemporizador tipo, String expresion) {
        assertThat(ExpresionDeTemporizador.problema(tipo, expresion)).get().asString().contains("ISO 8601");
    }

    @Test
    @DisplayName("Sin expresion o sin tipo no se sabe cuando salta")
    void incompletas() {
        assertThat(ExpresionDeTemporizador.problema(TipoTemporizador.DURACION, "  ")).get().asString()
                .contains("no dice cuando");
        assertThat(ExpresionDeTemporizador.problema(null, "PT2H")).get().asString().contains("no dice si espera");
    }
}
