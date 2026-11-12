package com.facimus.procesos.common.antivirus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** PR 61: el protocolo de clamd contra un clamd de mentira que lo habla de verdad. */
class ClamAvTest {

    private static ClamdFalso clamd;
    private ClamAv clamAv;

    @BeforeAll
    static void arrancar() {
        clamd = ClamdFalso.arrancar();
    }

    @AfterAll
    static void apagar() throws IOException {
        clamd.close();
    }

    @AfterEach
    void revisarDeNuevo() {
        clamd.contestar(null);
    }

    private ClamAv clamAv() {
        if (clamAv == null) {
            clamAv = new ClamAv("127.0.0.1", clamd.puerto(), Duration.ofSeconds(2));
        }
        return clamAv;
    }

    private static InputStream archivo(String texto) {
        return new ByteArrayInputStream(texto.getBytes(StandardCharsets.ISO_8859_1));
    }

    @Test
    @DisplayName("un archivo limpio vuelve limpio, y el antivirus se da por disponible")
    void limpio() {
        assertThat(clamAv().disponible()).isTrue();
        assertThat(clamAv().revisar(archivo("Instrucciones de empaque"))).isEqualTo(ResultadoDelAntivirus.sinHallazgos());
    }

    @Test
    @DisplayName("la cadena EICAR vuelve infectada, con el nombre de la firma que dio clamd")
    void eicar() {
        ResultadoDelAntivirus resultado = clamAv().revisar(archivo("antes " + ClamdFalso.EICAR + " despues"));

        assertThat(resultado.limpio()).isFalse();
        assertThat(resultado.firma()).isEqualTo("Eicar-Test-Signature");
    }

    @Test
    @DisplayName("un archivo mas grande que un trozo viaja entero, en varios trozos")
    void variosTrozos() {
        String grande = "a".repeat(20_000) + ClamdFalso.EICAR;

        assertThat(clamAv().revisar(archivo(grande)).limpio()).isFalse();
    }

    @Test
    @DisplayName("una respuesta que no es OK ni FOUND es un error, no un archivo limpio")
    void respuestaRara() {
        clamd.contestar("INSTREAM size limit exceeded. ERROR");
        InputStream contenido = archivo("x");

        assertThatThrownBy(() -> clamAv().revisar(contenido)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("size limit exceeded");
    }

    @Test
    @DisplayName("si clamd no contesta a tiempo, es un error que la cola reintenta")
    void sinRespuesta() {
        clamd.contestar("");
        ClamAv apurado = new ClamAv("127.0.0.1", clamd.puerto(), Duration.ofMillis(300));
        InputStream contenido = archivo("x");

        assertThatThrownBy(() -> apurado.revisar(contenido)).isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("si no hay clamd escuchando, es un error que dice donde lo busco")
    void apagado() throws IOException {
        int puerto;
        try (ServerSocket libre = new ServerSocket(0)) {
            puerto = libre.getLocalPort();
        }
        ClamAv sinServidor = new ClamAv("127.0.0.1", puerto, Duration.ofMillis(500));
        InputStream contenido = archivo("x");

        assertThatThrownBy(() -> sinServidor.revisar(contenido)).isInstanceOf(UncheckedIOException.class)
                .hasMessageContaining("127.0.0.1:" + puerto);
    }

    @Test
    @DisplayName("sin antivirus configurado no se finge una revision")
    void sinAntivirus() {
        SinAntivirus ninguno = new SinAntivirus();
        InputStream contenido = archivo("x");

        assertThat(ninguno.disponible()).isFalse();
        assertThatThrownBy(() -> ninguno.revisar(contenido)).isInstanceOf(IllegalStateException.class);
    }
}
