package com.facimus.procesos.common.almacen;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * PR 61: el almacen sin S3, en una carpeta del disco: para desarrollo, para las pruebas y para quien corre una sola
 * instancia. Cada clave es una ruta debajo de la raiz, y una clave nunca sale de ella. No puede prometer que algo no
 * cambie: el ancla de la bitacora queda escrita, pero cualquiera con acceso al disco la puede tocar.
 */
public class AlmacenEnDisco implements Almacen {

    private final Path raiz;

    public AlmacenEnDisco(Path raiz) {
        this.raiz = raiz.toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.raiz);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo crear la carpeta del almacen: " + this.raiz, e);
        }
    }

    @Override
    public void guardar(String clave, Path archivo, String tipo) {
        Path destino = ruta(clave);
        try {
            Files.createDirectories(destino.getParent());
            Files.copy(archivo, destino, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo guardar " + clave, e);
        }
    }

    @Override
    public InputStream abrir(String clave) {
        try {
            return Files.newInputStream(ruta(clave));
        } catch (NoSuchFileException e) {
            throw new ArchivoNoEncontradoException(clave);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo abrir " + clave, e);
        }
    }

    @Override
    public void borrarPrefijo(String prefijo) {
        Path carpeta = ruta(prefijo.endsWith("/") ? prefijo.substring(0, prefijo.length() - 1) : prefijo);
        if (!Files.exists(carpeta)) {
            return;
        }
        try (Stream<Path> dentro = Files.walk(carpeta)) {
            for (Path ruta : dentro.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(ruta);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo borrar " + prefijo, e);
        }
    }

    @Override
    public boolean guardarInmutable(String clave, byte[] contenido, Instant hasta) {
        Path destino = ruta(clave);
        try {
            Files.createDirectories(destino.getParent());
            Files.write(destino, contenido);
            destino.toFile().setReadOnly();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo guardar " + clave, e);
        }
        return false;
    }

    @Override
    public String descripcion() {
        return "disco en " + raiz;
    }

    /** La ruta de una clave, comprobada: debajo de la raiz y con los caracteres de una clave. */
    private Path ruta(String clave) {
        Path ruta = raiz.resolve(ClaveDeAlmacen.comprobar(clave)).normalize();
        if (!ruta.startsWith(raiz)) {
            throw new IllegalArgumentException("Clave fuera del almacen: " + clave);
        }
        return ruta;
    }
}
