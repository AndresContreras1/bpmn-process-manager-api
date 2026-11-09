package com.facimus.procesos.gestion.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.facimus.procesos.common.Huella;

/** PR 49: el cifrado del secreto del TOTP y la huella de los codigos de recuperacion. */
class CifradoDeSecretosTest {

    private static final String CLAVE = "una-clave-de-pruebas-con-mas-de-32-caracteres";
    private static final byte[] SECRETO = "doce-bytes-y-ocho-mas".getBytes(StandardCharsets.UTF_8);

    private final CifradoDeSecretos cifrado = new CifradoDeSecretos(CLAVE);

    @Test
    @DisplayName("Lo cifrado para un usuario se descifra para el, y nunca queda el secreto en claro")
    void idaYVuelta() {
        String guardado = cifrado.cifrar(SECRETO, 7L);

        assertThat(cifrado.descifrar(guardado, 7L)).isEqualTo(SECRETO);
        assertThat(new String(Base64.getDecoder().decode(guardado), StandardCharsets.ISO_8859_1))
                .doesNotContain(new String(SECRETO, StandardCharsets.ISO_8859_1));
    }

    @Test
    @DisplayName("Cada cifrado lleva su propio nonce: el mismo secreto no da dos veces lo mismo")
    void nonceAleatorio() {
        assertThat(cifrado.cifrar(SECRETO, 7L)).isNotEqualTo(cifrado.cifrar(SECRETO, 7L));
    }

    @Test
    @DisplayName("Un secreto copiado a la fila de otra persona no se descifra")
    void ligadoAlUsuario() {
        String guardado = cifrado.cifrar(SECRETO, 7L);

        assertThatThrownBy(() -> cifrado.descifrar(guardado, 8L)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Un byte cambiado en la base no se descifra: GCM lo nota")
    void alterado() {
        byte[] datos = Base64.getDecoder().decode(cifrado.cifrar(SECRETO, 7L));
        datos[datos.length - 1] ^= 1;
        String alterado = Base64.getEncoder().encodeToString(datos);

        assertThatThrownBy(() -> cifrado.descifrar(alterado, 7L)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Con otra clave no se descifra, y la huella de un codigo cambia")
    void otraClave() {
        CifradoDeSecretos otro = new CifradoDeSecretos(CLAVE + "-otra");
        String guardado = cifrado.cifrar(SECRETO, 7L);

        assertThatThrownBy(() -> otro.descifrar(guardado, 7L)).isInstanceOf(IllegalStateException.class);
        assertThat(otro.huella("k7p2mq9rt4")).isNotEqualTo(cifrado.huella("k7p2mq9rt4"));
    }

    @Test
    @DisplayName("La huella es un HMAC en hexadecimal, estable para el mismo codigo, y no un SHA-256 a secas")
    void huella() {
        assertThat(cifrado.huella("k7p2mq9rt4")).hasSize(64).matches("[0-9a-f]+")
                .isEqualTo(cifrado.huella("k7p2mq9rt4"))
                .isNotEqualTo(cifrado.huella("k7p2mq9rt5"))
                .isNotEqualTo(Huella.de("k7p2mq9rt4"));
    }

    @Test
    @DisplayName("Una clave de menos de 32 caracteres no arranca")
    void claveCorta() {
        assertThatThrownBy(() -> new CifradoDeSecretos("corta")).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MFA_CLAVE_DE_CIFRADO");
        assertThatThrownBy(() -> new CifradoDeSecretos("")).isInstanceOf(IllegalStateException.class);
    }
}
