package com.facimus.procesos.gestion.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.facimus.procesos.common.SolicitudInvalidaException;

/** NIST SP 800-63B-4 (3.1.1.2): largo, filtradas, repeticiones y series, y el contexto de quien elige la clave. */
class PoliticaDeClavesTest {

    private static final String[] ANA = {"Ana Gomez", "ana.gomez@acme.com", "Acme Store"};

    private final PoliticaDeClaves politica = new PoliticaDeClaves();

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"luz de la tarde en el puerto", "every-order-on-time", "girasoles en el campo",
            "ñandúes del parque", "Puente-De-Piedra"})
    @DisplayName("Una frase de 15 caracteres o mas sirve, sin mayusculas, numeros ni simbolos obligatorios")
    void unaFraseLarga_sirve(String clave) {
        assertThatCode(() -> politica.comprobar(clave, ANA)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("14 caracteres no alcanzan y 15 si, aunque los 14 lleven mayusculas, numeros y simbolos")
    void elMinimo_esDe15Caracteres() {
        assertThatThrownBy(() -> politica.comprobar("Pu3nte de p!ed", ANA))
                .isInstanceOf(SolicitudInvalidaException.class)
                .hasMessageContaining("al menos 15 caracteres");
        assertThatCode(() -> politica.comprobar("puente de piedr", ANA)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("El largo se cuenta en caracteres de verdad: ocho emojis son ocho, aunque Java los guarde en 16 chars")
    void elLargo_seCuentaEnCaracteresDeVerdad() {
        String emojis = "🌊🌲🌙🌵🌸🌼🍀🍁";
        assertThat(emojis).hasSize(16);

        assertThatThrownBy(() -> politica.comprobar(emojis, ANA))
                .isInstanceOf(SolicitudInvalidaException.class)
                .hasMessageContaining("al menos 15 caracteres");
    }

    @Test
    @DisplayName("72 bytes caben; con una eñe, los mismos 72 caracteres pasan de 72 bytes y BCrypt no los veria")
    void elMaximo_esDe72Bytes() {
        String justa = "luz de la tarde en el puerto viejo, ".repeat(2);
        String conEnie = justa.replaceFirst("n", "ñ");
        assertThat(justa.getBytes(StandardCharsets.UTF_8)).hasSize(PoliticaDeClaves.MAXIMO_BYTES);
        assertThat(conEnie).hasSize(72);

        assertThatCode(() -> politica.comprobar(justa, ANA)).doesNotThrowAnyException();
        assertThatThrownBy(() -> politica.comprobar(conEnie, ANA))
                .isInstanceOf(SolicitudInvalidaException.class)
                .hasMessageContaining("72 bytes");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"passwordpassword", "Password Password", "CONTRASEÑA SEGURA", "contrasena segura",
            "El veloz murciélago hindú", "qwertyuiopasdfgh"})
    @DisplayName("Las filtradas o las de siempre no sirven, escritas con mayusculas, tildes o espacios")
    void lasFiltradas_noSirven(String clave) {
        assertThatThrownBy(() -> politica.comprobar(clave, ANA))
                .isInstanceOf(SolicitudInvalidaException.class)
                .hasMessageContaining("filtradas");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"aaaaaaaaaaaaaaaa", "1212121212121212", "abcabcabcabcabcab", "abcdabcdabcdabcd",
            "abcdefghijklmnop", "zyxwvutsrqponmlk", "Ñ Ñ Ñ Ñ Ñ Ñ Ñ Ñ "})
    @DisplayName("Un caracter o un grupo de hasta cuatro repetido, o una serie, no sirven")
    void repetidasYSeries_noSirven(String clave) {
        assertThatThrownBy(() -> politica.comprobar(clave, ANA))
                .isInstanceOf(SolicitudInvalidaException.class)
                .hasMessageContaining("repetido");
    }

    @Test
    @DisplayName("Un grupo de cinco repetido ya no se busca: la regla es para lo que se adivina enseguida")
    void unGrupoDeCinco_noSeBusca() {
        assertThatCode(() -> politica.comprobar("perlaperlaperlaperla", ANA)).doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"gomez y su perro fiel", "la gomez de siempre", "acme acme y mas acme",
            "la mejor store del pueblo", "mi-proceso-bpmn-favorito"})
    @DisplayName("El nombre, el correo, la tienda o el servicio dentro de la clave no sirven")
    void elContexto_noSirve(String clave) {
        assertThatThrownBy(() -> politica.comprobar(clave, ANA))
                .isInstanceOf(SolicitudInvalidaException.class)
                .hasMessageContaining("tu nombre");
    }

    @Test
    @DisplayName("El contexto se compara sin tildes ni mayusculas: Jose esta dentro de JOSÉ")
    void elContexto_sinTildes() {
        assertThatThrownBy(() -> politica.comprobar("el cafe de JOSÉ en la esquina", "José Pérez", null, null))
                .isInstanceOf(SolicitudInvalidaException.class)
                .hasMessageContaining("tu nombre");
        assertThatThrownBy(() -> politica.comprobar("una tarde en el cafe", null, null, "Café Central"))
                .isInstanceOf(SolicitudInvalidaException.class)
                .hasMessageContaining("tu nombre");
    }

    @Test
    @DisplayName("Las palabras de menos de cuatro letras no se buscan, las de cuatro si")
    void lasPalabrasCortas_noSeBuscan() {
        assertThatCode(() -> politica.comprobar("ana y gil van al mar", "Ana Gil", "ana@gil.co", null))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> politica.comprobar("luis va al mar azul", "Luis", null, null))
                .isInstanceOf(SolicitudInvalidaException.class)
                .hasMessageContaining("tu nombre");
    }

    @Test
    @DisplayName("Sin contexto, la clave se mide igual")
    void sinContexto_seMideIgual() {
        assertThatCode(() -> politica.comprobar("luz de la tarde en el puerto")).doesNotThrowAnyException();
        assertThatThrownBy(() -> politica.comprobar("passwordpassword"))
                .isInstanceOf(SolicitudInvalidaException.class);
    }

    @Test
    @DisplayName("Normalizar quita mayusculas, tildes y espacios, y deja lo demas")
    void normalizar() {
        assertThat(PoliticaDeClaves.normalizar(" Contraseña\tSegura ÁÉÍÓÚ-2026 ")).isEqualTo("contrasenaseguraaeiou-2026");
    }
}
