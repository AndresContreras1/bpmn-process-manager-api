package com.facimus.procesos.common.archivos;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** PR 61: el tipo de un archivo sale de su contenido, y el nombre no puede disfrazarlo. */
class DetectorDeTipoTest {

    private final DetectorDeTipo detector = new DetectorDeTipo();

    @TempDir
    Path carpeta;

    private Optional<TipoDeArchivo> detectar(byte[] contenido, String nombre) throws IOException {
        Path archivo = Files.write(carpeta.resolve("subida"), contenido);
        return detector.detectar(archivo, nombre);
    }

    @Test
    @DisplayName("los tipos de la lista blanca se reconocen por sus bytes")
    void listaBlanca() throws IOException {
        assertThat(detectar(Archivos.PDF, "a")).contains(TipoDeArchivo.PDF);
        assertThat(detectar(Archivos.PNG, "a")).contains(TipoDeArchivo.PNG);
        assertThat(detectar(Archivos.JPEG, "a")).contains(TipoDeArchivo.JPEG);
        assertThat(detectar(Archivos.GIF, "a")).contains(TipoDeArchivo.GIF);
        assertThat(detectar(Archivos.SVG_CON_SCRIPT, "a")).contains(TipoDeArchivo.SVG);
        assertThat(detectar(Archivos.BPMN, "a")).contains(TipoDeArchivo.BPMN);
    }

    @Test
    @DisplayName("un documento de Office u OpenOffice se reconoce por su indice, no solo como un ZIP")
    void documentos() throws IOException {
        assertThat(detectar(Archivos.docx(), "a")).contains(TipoDeArchivo.DOCX);
        assertThat(detectar(Archivos.xlsx(), "a")).contains(TipoDeArchivo.XLSX);
        assertThat(detectar(Archivos.odt(), "a")).contains(TipoDeArchivo.ODT);
    }

    @Test
    @DisplayName("un ZIP que no es ningun documento no se acepta")
    void zipSuelto() throws IOException {
        assertThat(detectar(Archivos.zipSuelto(), "notas.docx")).isEmpty();
    }

    @Test
    @DisplayName("un ejecutable o un HTML con nombre de PDF o de imagen siguen siendo lo que son: no se aceptan")
    void disfrazados() throws IOException {
        assertThat(detectar(Archivos.EXE, "informe.pdf")).isEmpty();
        assertThat(detectar(Archivos.HTML, "foto.png")).isEmpty();
    }

    @Test
    @DisplayName("el texto plano se distingue entre texto, CSV y Markdown por la extension, que es lo unico que cambia")
    void texto() throws IOException {
        assertThat(detectar(Archivos.TEXTO, "nota.txt")).contains(TipoDeArchivo.TEXTO);
        assertThat(detectar(Archivos.TEXTO, "pedidos.CSV")).contains(TipoDeArchivo.CSV);
        assertThat(detectar(Archivos.TEXTO, "leeme.md")).contains(TipoDeArchivo.MARKDOWN);
        assertThat(detectar(Archivos.TEXTO, "leeme.markdown")).contains(TipoDeArchivo.MARKDOWN);
        assertThat(detectar(Archivos.TEXTO, null)).contains(TipoDeArchivo.TEXTO);
    }

    @Test
    @DisplayName("un XML que no es un BPMN no se acepta")
    void xmlCualquiera() throws IOException {
        assertThat(detectar("<?xml version=\"1.0\"?><pedido/>".getBytes(), "pedido.bpmn")).isEmpty();
    }
}
