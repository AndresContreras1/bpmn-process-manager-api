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
            "CICLO,R3/PT1H", "CICLO,R/P1D", "CICLO,R/2026-01-01T08:00:00Z/P1D", "CICLO,R2/PT1H/2026-01-01T08:00:00Z",
            "DURACION_HABIL,P2D", "DURACION_HABIL,PT4H", "DURACION_HABIL,P1DT2H", "CICLO,0 0 9 * * MON-FRI"})
    @DisplayName("Lo que ISO 8601 entiende no tiene problema")
    void validas(TipoTemporizador tipo, String expresion) {
        assertThat(ExpresionDeTemporizador.problema(tipo, expresion)).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"DURACION,two hours", "DURACION,2026-12-24T09:00:00Z", "FECHA,PT2H", "FECHA,2026-12-24", "CICLO,PT1H",
            "CICLO,R3", "CICLO,Rx/PT1H", "CICLO,R3/nada", "CICLO,R/2026-01-01T08:00:00Z",
            "CICLO,R/PT1H/PT1H/PT1H", "DURACION_HABIL,dos dias", "CICLO,cada lunes"})
    @DisplayName("Lo que no, dice que no lo es")
    void invalidas(TipoTemporizador tipo, String expresion) {
        assertThat(ExpresionDeTemporizador.problema(tipo, expresion)).get().asString().contains("ISO 8601");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {"DURACION|P999999999Y|cien anos", "DURACION|PT2562047788015215H|cien anos",
            "DURACION_HABIL|P3000D|366 jornadas", "FECHA|+999999-01-01T00:00:00|de 1900 a 9999",
            "CICLO|R/P999999999Y|cien anos", "CICLO|* * * * * *|una vez por minuto",
            "CICLO|R/PT0.000001S|una vez por minuto"})
    @DisplayName("PR 68: lo que se lee pero pasa los topes tambien es un problema (E-16), con el tope que pasa")
    void fueraDeLosTopes(TipoTemporizador tipo, String expresion, String tope) {
        assertThat(ExpresionDeTemporizador.problema(tipo, expresion)).get().asString().contains(tope);
    }

    @Test
    @DisplayName("PR 68: un mes habil no se entiende, y lo dice")
    void mesHabil() {
        assertThat(ExpresionDeTemporizador.problema(TipoTemporizador.DURACION_HABIL, "P1M")).get().asString()
                .contains("no es una duracion habil");
    }

    @Test
    @DisplayName("Sin expresion o sin tipo no se sabe cuando salta")
    void incompletas() {
        assertThat(ExpresionDeTemporizador.problema(TipoTemporizador.DURACION, "  ")).get().asString()
                .contains("no dice cuando");
        assertThat(ExpresionDeTemporizador.problema(null, "PT2H")).get().asString().contains("no dice si espera");
    }
}
