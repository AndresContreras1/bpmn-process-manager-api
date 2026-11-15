package com.facimus.procesos.common.formularios;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * PR 69: un campo se guarda siempre de una sola forma, la misma que vuelve del BPMN; si no, exportar e importar un
 * formulario, o guardarlo otra vez desde el modelador, lo contaria como un cambio que nadie hizo.
 */
class CampoDeFormularioTest {

    @Test
    @DisplayName("Un texto en blanco es ninguno, en cada propiedad que puede faltar")
    void textosEnBlanco() {
        CampoDeFormulario campo = new CampoDeFormulario("a", "A", TipoDeCampo.TEXTO, " ", null, null, null, null, null,
                "", "\t", " ", "", null, "  ", " ", "");

        assertThat(campo.ayuda()).isNull();
        assertThat(campo.patron()).isNull();
        assertThat(campo.desde()).isNull();
        assertThat(campo.hasta()).isNull();
        assertThat(campo.moneda()).isNull();
        assertThat(campo.porDefecto()).isNull();
        assertThat(campo.visibleSi()).isNull();
        assertThat(campo.requeridoSi()).isNull();
    }

    @Test
    @DisplayName("Lo que trae texto se queda como vino, espacios incluidos")
    void textosConAlgo() {
        CampoDeFormulario campo = new CampoDeFormulario("a", "A", TipoDeCampo.TEXTO, " ayuda ", null, null, null, null,
                null, " x", "2026-01-01", "2026-12-31", "COP", null, "order.total", "b == 1", "c == 2");

        assertThat(campo).extracting(CampoDeFormulario::ayuda, CampoDeFormulario::patron, CampoDeFormulario::desde,
                CampoDeFormulario::hasta, CampoDeFormulario::moneda, CampoDeFormulario::porDefecto,
                CampoDeFormulario::visibleSi, CampoDeFormulario::requeridoSi)
                .containsExactly(" ayuda ", " x", "2026-01-01", "2026-12-31", "COP", "order.total", "b == 1",
                        "c == 2");
    }

    @Test
    @DisplayName("requerido: false es no decirlo; true se queda")
    void requerido() {
        assertThat(conRequerido(Boolean.FALSE).requerido()).isNull();
        assertThat(conRequerido(null).requerido()).isNull();
        assertThat(conRequerido(Boolean.TRUE).requerido()).isTrue();
    }

    @Test
    @DisplayName("Una lista sin opciones es ninguna; con opciones, se queda con ellas")
    void opciones() {
        List<OpcionDeLista> una = List.of(new OpcionDeLista("WEB", "Web"));

        assertThat(conOpciones(List.of()).opciones()).isNull();
        assertThat(conOpciones(null).opciones()).isNull();
        assertThat(conOpciones(una).opciones()).isEqualTo(una);
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({"1E+3, 1000", "1000.00, 1000", "1.50, 1.5", "-2.500, -2.5", "0.000, 0", "0, 0", "7, 7",
        "1E+15, 1000000000000000", "120E-1, 12"})
    @DisplayName("Un limite va sin ceros de sobra ni exponente, con la escala que vuelve del BPMN")
    void limitesPlanos(String escrito, String guardado) {
        CampoDeFormulario campo = conLimites(new BigDecimal(escrito), new BigDecimal(escrito));

        assertThat(campo.minimo()).isEqualTo(new BigDecimal(guardado));
        assertThat(campo.maximo()).isEqualTo(new BigDecimal(guardado));
        assertThat(campo.minimo().toPlainString()).isEqualTo(guardado);
    }

    @Test
    @DisplayName("Un limite que no cabe se deja como vino, y rapido: escribirlo entero no terminaria")
    void limitesQueNoCaben() {
        BigDecimal enorme = new BigDecimal("1E+999999999");
        BigDecimal justoFuera = new BigDecimal("1E+16");

        CampoDeFormulario campo = assertTimeoutPreemptively(Duration.ofSeconds(2),
                () -> conLimites(enorme, justoFuera));

        assertThat(campo.minimo()).isSameAs(enorme);
        assertThat(campo.maximo()).isSameAs(justoFuera);
        assertThat(conLimites(null, null).minimo()).isNull();
    }

    @Test
    @DisplayName("La etiqueta en blanco de una opcion es ninguna")
    void etiquetaDeLaOpcion() {
        assertThat(new OpcionDeLista("WEB", " ").etiqueta()).isNull();
        assertThat(new OpcionDeLista("WEB", null).etiqueta()).isNull();
        assertThat(new OpcionDeLista("WEB", "Web").etiqueta()).isEqualTo("Web");
    }

    private static CampoDeFormulario conRequerido(Boolean requerido) {
        return new CampoDeFormulario("a", "A", TipoDeCampo.SI_NO, null, requerido, null, null, null, null, null, null,
                null, null, null, null, null, null);
    }

    private static CampoDeFormulario conOpciones(List<OpcionDeLista> opciones) {
        return new CampoDeFormulario("a", "A", TipoDeCampo.LISTA, null, null, null, null, null, null, null, null, null,
                null, opciones, null, null, null);
    }

    private static CampoDeFormulario conLimites(BigDecimal minimo, BigDecimal maximo) {
        return new CampoDeFormulario("a", "A", TipoDeCampo.NUMERO, null, null, minimo, maximo, null, null, null, null,
                null, null, null, null, null, null);
    }
}
