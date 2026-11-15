package com.facimus.procesos.common.archivos;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** PR 61: el nombre con que se guarda y se descarga un archivo. */
class NombreDeArchivoTest {

    @Test
    @DisplayName("un nombre normal queda como estaba, con la extension de su tipo real")
    void normal() {
        assertThat(NombreDeArchivo.sanear("Instrucciones de empaque.pdf", TipoDeArchivo.PDF))
                .isEqualTo("Instrucciones de empaque.pdf");
        assertThat(NombreDeArchivo.sanear("foto.jpeg", TipoDeArchivo.JPEG)).isEqualTo("foto.jpg");
        assertThat(NombreDeArchivo.sanear("informe.pdf", TipoDeArchivo.PNG)).isEqualTo("informe.png");
    }

    @Test
    @DisplayName("de una ruta solo queda el ultimo tramo, sea con barras o con barras invertidas")
    void rutas() {
        assertThat(NombreDeArchivo.sanear("../../etc/passwd", TipoDeArchivo.TEXTO)).isEqualTo("passwd.txt");
        assertThat(NombreDeArchivo.sanear("C:\\Users\\ana\\nota.txt", TipoDeArchivo.TEXTO)).isEqualTo("nota.txt");
    }

    @Test
    @DisplayName("sin caracteres de control, de formato ni los que un sistema de archivos no acepta")
    void caracteres() {
        assertThat(NombreDeArchivo.sanear("a\u0000b\nc<d>e:f\"g|h?i*j.txt", TipoDeArchivo.TEXTO))
                .isEqualTo("abcdefghij.txt");
        // El caracter que invierte el texto haria que "gpj.exe" se leyera al reves
        assertThat(NombreDeArchivo.sanear("factura\u202Egpj.exe", TipoDeArchivo.PDF)).isEqualTo("facturagpj.pdf");
    }

    @Test
    @DisplayName("sin puntos ni espacios al principio o al final")
    void puntos() {
        assertThat(NombreDeArchivo.sanear("  ..oculto.txt. ", TipoDeArchivo.TEXTO)).isEqualTo("oculto.txt");
    }

    @Test
    @DisplayName("vacio, sin nombre o un nombre que Windows reserva pasa a ser documento")
    void porDefecto() {
        assertThat(NombreDeArchivo.sanear(null, TipoDeArchivo.PDF)).isEqualTo("documento.pdf");
        assertThat(NombreDeArchivo.sanear("   ", TipoDeArchivo.PDF)).isEqualTo("documento.pdf");
        assertThat(NombreDeArchivo.sanear("CON.txt", TipoDeArchivo.TEXTO)).isEqualTo("documento.txt");
        assertThat(NombreDeArchivo.sanear("/", TipoDeArchivo.TEXTO)).isEqualTo("documento.txt");
    }

    @Test
    @DisplayName("un nombre larguisimo se corta, sin partir un caracter y conservando la extension")
    void largo() {
        String nombre = NombreDeArchivo.sanear("ñ".repeat(300) + ".pdf", TipoDeArchivo.PDF);

        assertThat(nombre).hasSize(150).endsWith(".pdf").startsWith("ñññ");
    }

    @Test
    @DisplayName("se normaliza a NFC: la misma letra escrita de dos formas da el mismo nombre")
    void normalizado() {
        assertThat(NombreDeArchivo.sanear("Camio\u0301n.txt", TipoDeArchivo.TEXTO)).isEqualTo("Cami\u00f3n.txt");
    }
}
