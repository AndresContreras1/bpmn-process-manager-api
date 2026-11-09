package com.facimus.procesos.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** PR 49: el TOTP escrito en el proyecto contra los vectores del propio RFC 6238. */
class TotpTest {

    /** La semilla de los vectores de SHA-1 del apendice B del RFC 6238. */
    private static final byte[] SEMILLA = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

    @ParameterizedTest(name = "a los {0} s el codigo es {1}")
    @CsvSource({
            "59, 94287082",
            "1111111109, 07081804",
            "1111111111, 14050471",
            "1234567890, 89005924",
            "2000000000, 69279037",
            "20000000000, 65353130"
    })
    @DisplayName("Da los codigos de 8 digitos de los vectores SHA-1 del RFC 6238")
    void vectoresDelRfc(long segundos, String esperado) {
        assertThat(Totp.codigo(SEMILLA, Totp.paso(Instant.ofEpochSecond(segundos)), 8)).isEqualTo(esperado);
    }

    @Test
    @DisplayName("Los pasos son de 30 segundos desde la epoca Unix")
    void pasosDeTreintaSegundos() {
        assertThat(Totp.paso(Instant.ofEpochSecond(0))).isZero();
        assertThat(Totp.paso(Instant.ofEpochSecond(29))).isZero();
        assertThat(Totp.paso(Instant.ofEpochSecond(30))).isEqualTo(1);
        assertThat(Totp.paso(Instant.ofEpochSecond(59))).isEqualTo(1);
    }

    @Test
    @DisplayName("Acepta el codigo de ahora y el de un paso a cada lado, y dice en que paso lo acepto")
    void aceptaUnPasoACadaLado() {
        Instant ahora = Instant.ofEpochSecond(1_700_000_000L);
        long paso = Totp.paso(ahora);

        assertThat(Totp.verificar(SEMILLA, Totp.codigo(SEMILLA, paso, 6), ahora, 0)).hasValue(paso);
        assertThat(Totp.verificar(SEMILLA, Totp.codigo(SEMILLA, paso - 1, 6), ahora, 0)).hasValue(paso - 1);
        assertThat(Totp.verificar(SEMILLA, Totp.codigo(SEMILLA, paso + 1, 6), ahora, 0)).hasValue(paso + 1);
    }

    @Test
    @DisplayName("No acepta un codigo de dos pasos atras ni de dos pasos adelante")
    void rechazaFueraDeLaVentana() {
        Instant ahora = Instant.ofEpochSecond(1_700_000_000L);
        long paso = Totp.paso(ahora);

        assertThat(Totp.verificar(SEMILLA, Totp.codigo(SEMILLA, paso - 2, 6), ahora, 0)).isEmpty();
        assertThat(Totp.verificar(SEMILLA, Totp.codigo(SEMILLA, paso + 2, 6), ahora, 0)).isEmpty();
    }

    @Test
    @DisplayName("Un codigo de un paso ya usado, o anterior, no vuelve a entrar")
    void noAceptaUnPasoYaUsado() {
        Instant ahora = Instant.ofEpochSecond(1_700_000_000L);
        long paso = Totp.paso(ahora);
        String codigo = Totp.codigo(SEMILLA, paso, 6);

        assertThat(Totp.verificar(SEMILLA, codigo, ahora, paso)).isEmpty();
        assertThat(Totp.verificar(SEMILLA, Totp.codigo(SEMILLA, paso - 1, 6), ahora, paso)).isEmpty();
        assertThat(Totp.verificar(SEMILLA, Totp.codigo(SEMILLA, paso + 1, 6), ahora, paso)).hasValue(paso + 1);
    }

    @Test
    @DisplayName("Lo que no son seis digitos no es un codigo")
    void soloSeisDigitos() {
        Instant ahora = Instant.ofEpochSecond(1_700_000_000L);
        String bueno = Totp.codigo(SEMILLA, Totp.paso(ahora), 6);

        assertThat(Totp.verificar(SEMILLA, null, ahora, 0)).isEmpty();
        assertThat(Totp.verificar(SEMILLA, bueno.substring(1), ahora, 0)).isEmpty();
        assertThat(Totp.verificar(SEMILLA, bueno + "0", ahora, 0)).isEmpty();
        assertThat(Totp.verificar(SEMILLA, "12a456", ahora, 0)).isEmpty();
    }
}
