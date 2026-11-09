package com.facimus.procesos.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** PR 49: el Base32 del secreto del TOTP, contra los vectores del RFC 4648 sin relleno. */
class Base32Test {

    @ParameterizedTest(name = "\"{0}\" es {1}")
    @CsvSource({
            "f, MY",
            "fo, MZXQ",
            "foo, MZXW6",
            "foob, MZXW6YQ",
            "fooba, MZXW6YTB",
            "foobar, MZXW6YTBOI"
    })
    @DisplayName("Codifica y decodifica los vectores del RFC 4648")
    void vectoresDelRfc(String texto, String esperado) {
        byte[] datos = texto.getBytes(StandardCharsets.US_ASCII);
        assertThat(Base32.codificar(datos)).isEqualTo(esperado);
        assertThat(Base32.decodificar(esperado)).isEqualTo(datos);
    }

    @Test
    @DisplayName("Al leer ignora minusculas, espacios, guiones y el relleno")
    void leeComoLoEscribeUnaPersona() {
        assertThat(Base32.decodificar("mzxw 6ytb-oi======")).isEqualTo("foobar".getBytes(StandardCharsets.US_ASCII));
    }

    @Test
    @DisplayName("Ida y vuelta de un secreto de 20 bytes")
    void idaYVuelta() {
        byte[] secreto = new byte[20];
        new SecureRandom().nextBytes(secreto);
        assertThat(Base32.codificar(secreto)).hasSize(32);
        assertThat(Base32.decodificar(Base32.codificar(secreto))).isEqualTo(secreto);
    }

    @Test
    @DisplayName("Un caracter fuera del alfabeto no se decodifica")
    void fueraDelAlfabeto() {
        assertThatThrownBy(() -> Base32.decodificar("MZXW1")).isInstanceOf(IllegalArgumentException.class);
    }
}
