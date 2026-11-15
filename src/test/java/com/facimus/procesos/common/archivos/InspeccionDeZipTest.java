package com.facimus.procesos.common.archivos;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** PR 61: un ZIP se abre de verdad, contando lo que sale, y se corta antes de que haga dano. */
class InspeccionDeZipTest {

    private static final long MEGA = 1024 * 1024;

    @TempDir
    Path carpeta;

    private Path guardar(byte[] contenido) throws IOException {
        return Files.write(carpeta.resolve("subida.zip"), contenido);
    }

    @Test
    @DisplayName("un documento normal se puede abrir")
    void normal() throws IOException {
        InspeccionDeZip inspeccion = new InspeccionDeZip(200 * MEGA, 100, 10_000);

        assertThat(inspeccion.problema(guardar(Archivos.docx()))).isEmpty();
        assertThat(inspeccion.problema(guardar(Archivos.odt()))).isEmpty();
    }

    @Test
    @DisplayName("una bomba que pesa KB y crece a megas se corta por la proporcion")
    void bombaPorProporcion() throws IOException {
        Path bomba = guardar(Archivos.bomba(40));
        InspeccionDeZip inspeccion = new InspeccionDeZip(200 * MEGA, 100, 10_000);

        assertThat(Files.size(bomba)).isLessThan(MEGA);
        assertThat(inspeccion.problema(bomba)).contains("El archivo crece demasiado al abrirlo.");
    }

    @Test
    @DisplayName("y por el total, aunque la proporcion le alcance")
    void bombaPorTotal() throws IOException {
        InspeccionDeZip inspeccion = new InspeccionDeZip(10 * MEGA, 1_000_000, 10_000);

        assertThat(inspeccion.problema(guardar(Archivos.bomba(20)))).contains("El archivo crece demasiado al abrirlo.");
    }

    @Test
    @DisplayName("demasiadas partes se cortan aunque cada una sea pequena")
    void demasiadasEntradas() throws IOException {
        Map<String, byte[]> entradas = new HashMap<>();
        for (int i = 0; i < 30; i++) {
            entradas.put("parte" + i + ".xml", new byte[] {'x'});
        }
        InspeccionDeZip inspeccion = new InspeccionDeZip(200 * MEGA, 100, 20);

        assertThat(inspeccion.problema(guardar(Archivos.zip(entradas)))).contains("El archivo tiene más de 20 partes.");
    }

    @Test
    @DisplayName("una entrada que se saldria de su carpeta al extraerse se rechaza")
    void rutaQueSale() throws IOException {
        InspeccionDeZip inspeccion = new InspeccionDeZip(200 * MEGA, 100, 10_000);

        assertThat(inspeccion.problema(guardar(Archivos.zipQueSale())))
                .contains("El archivo tiene una ruta que sale de él.");
        assertThat(inspeccion.problema(guardar(Archivos.zip(Map.of("/etc/x", new byte[] {1})))))
                .contains("El archivo tiene una ruta que sale de él.");
        assertThat(inspeccion.problema(guardar(Archivos.zip(Map.of("C:\\Windows\\x", new byte[] {1})))))
                .contains("El archivo tiene una ruta que sale de él.");
        assertThat(inspeccion.problema(guardar(Archivos.zip(Map.of("docs/a..b.xml", new byte[] {1}))))).isEmpty();
    }

    @Test
    @DisplayName("lo que no es un ZIP sano se da por danado")
    void danado() throws IOException {
        InspeccionDeZip inspeccion = new InspeccionDeZip(200 * MEGA, 100, 10_000);
        byte[] docx = Archivos.docx();
        byte[] cortado = Arrays.copyOf(docx, docx.length / 3);

        assertThat(inspeccion.problema(guardar(cortado))).contains("El archivo está dañado.");
        assertThat(inspeccion.problema(carpeta.resolve("no-existe.zip"))).contains("No se pudo leer el archivo.");
    }
}
