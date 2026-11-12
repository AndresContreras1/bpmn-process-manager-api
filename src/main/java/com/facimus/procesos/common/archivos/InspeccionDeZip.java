package com.facimus.procesos.common.archivos;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * PR 61: un documento de Office es un ZIP, y un ZIP puede ser una bomba: unos KB que al abrirse son gigas. Aqui se
 * descomprime de verdad, entrada por entrada y sin guardar nada, contando lo que sale: lo que el indice declara se
 * puede falsear, lo que sale no. Se corta al pasar el total, la proporcion o la cantidad de entradas, y antes de
 * leer mas de la cuenta.
 */
public class InspeccionDeZip {

    private final long maximoDescomprimido;
    private final long proporcionMaxima;
    private final int maximoDeEntradas;

    public InspeccionDeZip(long maximoDescomprimido, long proporcionMaxima, int maximoDeEntradas) {
        this.maximoDescomprimido = maximoDescomprimido;
        this.proporcionMaxima = proporcionMaxima;
        this.maximoDeEntradas = maximoDeEntradas;
    }

    /** El motivo para rechazarlo, o vacio si se puede abrir sin peligro. */
    public Optional<String> problema(Path archivo) {
        long comprimido;
        try {
            comprimido = Math.max(1, Files.size(archivo));
        } catch (IOException e) {
            return Optional.of("No se pudo leer el archivo.");
        }
        long tope = Math.min(maximoDescomprimido, comprimido * proporcionMaxima);
        long total = 0;
        int entradas = 0;
        byte[] buffer = new byte[8192];
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(archivo))) {
            for (ZipEntry entrada = zip.getNextEntry(); entrada != null; entrada = zip.getNextEntry()) {
                if (++entradas > maximoDeEntradas) {
                    return Optional.of("El archivo tiene más de " + maximoDeEntradas + " partes.");
                }
                if (saleDelZip(entrada.getName())) {
                    return Optional.of("El archivo tiene una ruta que sale de él.");
                }
                total += leer(zip, buffer, tope - total);
                if (total > tope) {
                    return Optional.of("El archivo crece demasiado al abrirlo.");
                }
            }
        } catch (IOException | IllegalArgumentException e) {
            return Optional.of("El archivo está dañado.");
        }
        return Optional.empty();
    }

    /** Una ruta absoluta, de Windows o con un {@code ..}: al extraerla, quedaria fuera de su carpeta. */
    private static boolean saleDelZip(String nombre) {
        String normal = nombre.replace('\\', '/');
        return normal.startsWith("/") || normal.matches("^[A-Za-z]:.*") || List.of(normal.split("/")).contains("..");
    }

    /** Lee la entrada hasta su fin o hasta pasar lo que queda; devuelve lo leido. */
    private static long leer(InputStream entrada, byte[] buffer, long queda) throws IOException {
        long leidos = 0;
        int n;
        while ((n = entrada.read(buffer)) > 0) {
            leidos += n;
            if (leidos > queda) {
                return leidos;
            }
        }
        return leidos;
    }
}
