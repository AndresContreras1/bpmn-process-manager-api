package com.facimus.procesos.modelado.dto.response;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.facimus.procesos.common.condiciones.ArbolDeCondicion;
import com.facimus.procesos.common.formularios.CampoDeFormulario;
import com.facimus.procesos.common.formularios.TipoDeCampo;

/** PR 69: cada respuesta con un formulario lleva sus condiciones compiladas; una que no compila no la rompe. */
class CampoDeFormularioResponseTest {

    @Test
    @DisplayName("Las condiciones van compiladas junto a su texto")
    void compiladas() {
        CampoDeFormularioResponse campo = CampoDeFormularioResponse.de(conCondiciones("not (a == 1)", "b != 'x'"));

        assertThat(campo.visibleSiArbol().tipo()).isEqualTo(ArbolDeCondicion.NO);
        assertThat(campo.requeridoSiArbol().ruta()).isEqualTo("b");
        assertThat(campo.campo()).isEqualTo(conCondiciones("not (a == 1)", "b != 'x'"));
    }

    @Test
    @DisplayName("Una condicion mal escrita llega sin arbol")
    void malEscrita() {
        CampoDeFormularioResponse campo = CampoDeFormularioResponse.de(conCondiciones("a ==", "and"));

        assertThat(campo.visibleSiArbol()).isNull();
        assertThat(campo.requeridoSiArbol()).isNull();
    }

    @Test
    @DisplayName("Una condicion de mas de 1000 caracteres, que solo trae un BPMN, no se compila: llega sin arbol")
    void demasiadoLarga() {
        String enorme = "not ".repeat(25_000) + "a == 1";
        String justa = "not ".repeat(248) + "a == 1  ";
        assertThat(justa).hasSize(CampoDeFormulario.LARGO_DE_CONDICION);

        CampoDeFormularioResponse campo = assertTimeoutPreemptively(Duration.ofSeconds(2),
                () -> CampoDeFormularioResponse.de(conCondiciones(enorme, justa + " ")));
        CampoDeFormularioResponse enElTope = CampoDeFormularioResponse.de(conCondiciones(justa, null));

        assertThat(campo.visibleSiArbol()).isNull();
        assertThat(campo.requeridoSiArbol()).isNull();
        assertThat(campo.visibleSi()).isEqualTo(enorme);
        assertThat(enElTope.visibleSiArbol().tipo()).isEqualTo(ArbolDeCondicion.NO);
    }

    private static CampoDeFormulario conCondiciones(String visibleSi, String requeridoSi) {
        return new CampoDeFormulario("c", "C", TipoDeCampo.TEXTO, null, null, null, null, null, null, null, null, null,
                null, null, null, visibleSi, requeridoSi);
    }
}
