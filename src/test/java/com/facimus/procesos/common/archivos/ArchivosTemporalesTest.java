package com.facimus.procesos.common.archivos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.facimus.procesos.common.archivos.ArchivosTemporales.Temporal;

/** PR 61: los archivos de paso viven en una carpeta propia y se borran al cerrarse. */
class ArchivosTemporalesTest {

    @TempDir
    Path raiz;

    @Test
    @DisplayName("crea la carpeta que falte y cada archivo nuevo queda dentro, vacio, con el prefijo pedido")
    void nuevo() throws IOException {
        Path carpeta = raiz.resolve("api/paso");
        ArchivosTemporales temporales = new ArchivosTemporales(carpeta);

        try (Temporal temporal = temporales.nuevo("subida-")) {
            assertThat(temporal.ruta()).exists().hasParent(carpeta.toAbsolutePath().normalize());
            assertThat(temporal.ruta().getFileName().toString()).startsWith("subida-").endsWith(".tmp");
            assertThat(Files.size(temporal.ruta())).isZero();
        }
    }

    @Test
    @DisplayName("cerrarlo lo borra, haya quedado vacio o lleno, y cerrarlo dos veces no falla")
    void cerrar() throws IOException {
        ArchivosTemporales temporales = new ArchivosTemporales(raiz);
        Temporal temporal = temporales.nuevo("zip-");
        Files.writeString(temporal.ruta(), "contenido");

        temporal.close();
        temporal.close();

        assertThat(temporal.ruta()).doesNotExist();
        assertThat(raiz).isEmptyDirectory();
    }

    @Test
    @DisplayName("donde hay permisos POSIX, solo la API entra a la carpeta")
    void soloLaApi() throws IOException {
        Path carpeta = raiz.resolve("privada");

        new ArchivosTemporales(carpeta);

        if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
            assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(carpeta))).isEqualTo("rwx------");
        } else {
            assertThat(carpeta).isDirectory();
        }
    }

    @Test
    @DisplayName("si la carpeta no se puede crear, lo dice al arrancar y no al primer archivo")
    void carpetaImposible() throws IOException {
        Path archivo = Files.writeString(raiz.resolve("ocupado"), "x");
        Path dentroDeUnArchivo = archivo.resolve("paso");

        assertThatThrownBy(() -> new ArchivosTemporales(dentroDeUnArchivo)).isInstanceOf(UncheckedIOException.class)
                .hasMessageContaining("archivos de paso");
    }

    @Test
    @DisplayName("si la carpeta desaparece, pedir un archivo es un error claro")
    void sinCarpeta() throws IOException {
        Path carpeta = raiz.resolve("se-va");
        ArchivosTemporales temporales = new ArchivosTemporales(carpeta);
        Files.delete(carpeta);

        assertThatThrownBy(() -> temporales.nuevo("x-")).isInstanceOf(UncheckedIOException.class)
                .hasMessageContaining("archivo de paso");
    }
}
