package com.facimus.procesos.common.archivos;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

/**
 * PR 61: los archivos de paso (lo que llega antes de ir al almacen, lo que se arma antes de guardarse) van en una
 * * carpeta propia de la API, no en la temporal que comparte el sistema entero: nadie mas la lee ni deja nada ahi. Donde
 * el sistema de archivos tiene permisos POSIX, solo la API puede entrar. Cada archivo se borra al cerrarse.
 */
public class ArchivosTemporales {

    private final Path carpeta;

    public ArchivosTemporales(Path carpeta) {
        this.carpeta = carpeta.toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.carpeta);
            if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
                Files.setPosixFilePermissions(this.carpeta, PosixFilePermissions.fromString("rwx------"));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo preparar la carpeta de archivos de paso: " + this.carpeta, e);
        }
    }

    /** Un archivo nuevo y vacio en la carpeta; se borra al cerrarlo. */
    public Temporal nuevo(String prefijo) {
        try {
            return new Temporal(Files.createTempFile(carpeta, prefijo, ".tmp"));
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo crear un archivo de paso", e);
        }
    }

    /** Un archivo de paso. Cerrarlo lo borra; si el sistema no lo deja borrar ahora, se borra al apagar. */
    public record Temporal(Path ruta) implements AutoCloseable {

        @Override
        public void close() {
            try {
                Files.deleteIfExists(ruta);
            } catch (IOException _) {
                ruta.toFile().deleteOnExit();
            }
        }
    }
}
