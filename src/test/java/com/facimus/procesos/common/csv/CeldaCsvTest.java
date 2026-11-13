package com.facimus.procesos.common.csv;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** La celda de CSV que comparten la bitacora y la exportacion. */
class CeldaCsvTest {

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"=1+1", "+1", "-1", "@SUM(A1)", "\tx", "\rx"})
    @DisplayName("un texto que una hoja de calculo leeria como formula lleva un apostrofo delante")
    void formulas(String peligroso) {
        assertThat(CeldaCsv.de(peligroso).replace("\"", "")).isEqualTo("'" + peligroso);
    }

    @Test
    @DisplayName("lo que no es una formula queda como esta: un numero negativo, un texto comun, nada")
    void sinFormula() {
        assertThat(CeldaCsv.de(-5)).isEqualTo("-5");
        assertThat(CeldaCsv.de(-2.5)).isEqualTo("-2.5");
        assertThat(CeldaCsv.de(-7L)).isEqualTo("-7");
        assertThat(CeldaCsv.de("Despacho")).isEqualTo("Despacho");
        assertThat(CeldaCsv.de("a=b")).isEqualTo("a=b");
        assertThat(CeldaCsv.de("")).isEmpty();
        assertThat(CeldaCsv.de(null)).isEmpty();
    }

    @Test
    @DisplayName("lo que lleva comas, comillas o saltos va entre comillas, con las comillas dobladas")
    void comillas() {
        assertThat(CeldaCsv.de("a,b")).isEqualTo("\"a,b\"");
        assertThat(CeldaCsv.de("\rx")).isEqualTo("\"'\rx\"");
        assertThat(CeldaCsv.de("dos\rlineas")).isEqualTo("\"dos\rlineas\"");
        assertThat(CeldaCsv.de("dos\nlineas")).isEqualTo("\"dos\nlineas\"");
        assertThat(CeldaCsv.de("una \"cita\"")).isEqualTo("\"una \"\"cita\"\"\"");
        assertThat(CeldaCsv.de("=HYPERLINK(\"x\")")).isEqualTo("\"'=HYPERLINK(\"\"x\"\")\"");
    }
}
