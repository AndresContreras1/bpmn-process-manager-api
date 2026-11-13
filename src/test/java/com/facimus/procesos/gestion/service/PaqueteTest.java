package com.facimus.procesos.gestion.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.facimus.procesos.gestion.service.Paquete.Tramos;

/** PR 66: como un texto libre entra en una ruta del paquete, y que tramos acepta el paquete. */
class PaqueteTest {

    @ParameterizedTest(name = "\"{0}\" -> {1}")
    @CsvSource(delimiter = '|', value = {
            "Instrucciones de empaque.pdf | Instrucciones-de-empaque.pdf",
            "Política de envíos.docx       | Politica-de-envios.docx",
            "../../etc/passwd             | etc-passwd",
            "..\\..\\windows\\win.ini    | windows-win.ini",
            ".oculto                      | oculto",
            "  --raro--.  .               | raro",
            "a--b__c.d                    | a--b__c.d",
            "factura #12 (copia).pdf      | factura-12-copia-.pdf",
            "日本語.txt                    | txt",
            "...                          | sin-nombre",
            "%%%                          | sin-nombre"})
    @DisplayName("un nombre libre queda en un tramo seguro: sin tildes, sin rutas, sin puntos al principio")
    void tramo(String libre, String esperado) {
        assertThat(Paquete.tramo(libre)).isEqualTo(esperado);
        assertThat(Tramos.esValido(Paquete.tramo(libre))).isTrue();
    }

    @Test
    @DisplayName("nada, o un texto largo, tambien quedan en un tramo valido de 80 caracteres como mucho")
    void tramoDeLoQueSea() {
        assertThat(Paquete.tramo(null)).isEqualTo("sin-nombre");
        assertThat(Paquete.tramo("")).isEqualTo("sin-nombre");
        assertThat(Paquete.tramo("x".repeat(200))).hasSize(80);
        assertThat(Paquete.tramo("ab" + "-".repeat(100) + "cd")).isEqualTo("ab");
        assertThat(Paquete.tramo("a  b")).isEqualTo("a-b");
    }

    @Test
    @DisplayName("la carpeta de un proceso lleva su id y su nombre legible")
    void carpetaDeProceso() {
        assertThat(Paquete.carpetaDeProceso(7, "Order Fulfillment")).isEqualTo("procesos/7-order-fulfillment");
        assertThat(Paquete.carpetaDeProceso(8, "Devolución / reembolso")).isEqualTo("procesos/8-devolucion-reembolso");
    }

    @ParameterizedTest(name = "\"{0}\" valido: {1}")
    @CsvSource(delimiter = '|', value = {"a | true", "A1.b-c_d | true", "9 | true", ". | false", ".a | false",
        "-a | false", "_a | false", "a b | false", "a/b | false", "a\\b | false", "á | false", "a~ | false"})
    @DisplayName("un tramo empieza por letra o numero ASCII y sigue con letras, numeros, punto, guion o guion bajo")
    void tramosValidos(String tramo, boolean valido) {
        assertThat(Tramos.esValido(tramo)).isEqualTo(valido);
        assertThat(Tramos.esValido("")).isFalse();
    }
}
