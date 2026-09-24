package com.facimus.procesos.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** SHA-256 de verdad, en hexadecimal y en minusculas: lo que se guarda tiene que poder compararse entre maquinas. */
class HuellaTest {

    @Test
    @DisplayName("La huella de un texto es su SHA-256 en hexadecimal")
    void de_texto_esElSha256Conocido() {
        assertThat(Huella.de("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    @DisplayName("El mismo texto da siempre la misma huella y otro texto da otra")
    void de_textosDistintos_huellasDistintas() {
        assertThat(Huella.de("Order fulfillment")).isEqualTo(Huella.de("Order fulfillment"));
        assertThat(Huella.de("Order fulfillment")).isNotEqualTo(Huella.de("Order fulfilment"));
    }

    @Test
    @DisplayName("Varias partes se encadenan: una huella por partes es la del texto entero")
    void de_variasPartes_seEncadenan() {
        assertThat(Huella.de("ab".getBytes(StandardCharsets.UTF_8), "c".getBytes(StandardCharsets.UTF_8)))
                .isEqualTo(Huella.de("abc"));
    }

    @Test
    @DisplayName("Mover el corte entre las partes no cambia la huella, pero cambiar su orden si")
    void de_ordenDeLasPartes_cambiaLaHuella() {
        byte[] primera = "POST /api/v1/procesos".getBytes(StandardCharsets.UTF_8);
        byte[] segunda = "{}".getBytes(StandardCharsets.UTF_8);

        assertThat(Huella.de(primera, segunda)).isNotEqualTo(Huella.de(segunda, primera));
    }
}
