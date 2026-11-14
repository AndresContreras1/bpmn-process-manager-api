package com.facimus.procesos.gestion.service.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.facimus.procesos.common.csv.CeldaCsv;
import com.facimus.procesos.gestion.service.Paquete;
import com.facimus.procesos.gestion.service.Tramos;

import tools.jackson.databind.json.JsonMapper;

/**
 * PR 66: el paquete de una exportacion como ZIP, escrito de corrido sobre un flujo (el archivo de paso de la
 * exportacion): nada se arma entero en memoria. Mide cada archivo al escribirlo, su tamano y su SHA-256, y al terminar
 * escribe el manifiesto con esa lista, que es el ultimo archivo del ZIP. Tambien mide el ZIP entero. No cierra el flujo
 * que recibe: es de quien lo abrio.
 */
final class PaqueteZip implements Paquete, AutoCloseable {

    /** Lo que mide una ruta como mucho, lo que aceptan los sistemas de archivos de siempre. */
    static final int LARGO_MAXIMO_DE_RUTA = 255;

    private static final byte[] APERTURA = "[".getBytes(StandardCharsets.UTF_8);
    private static final byte[] SALTO = "\n".getBytes(StandardCharsets.UTF_8);
    private static final byte[] SEPARADOR = ",\n".getBytes(StandardCharsets.UTF_8);
    private static final byte[] CIERRE = "\n]\n".getBytes(StandardCharsets.UTF_8);
    private static final String FIN_DE_LINEA_CSV = "\r\n";
    private static final String LA_RUTA = "La ruta ";

    private final JsonMapper json;
    private final Medidor total;
    private final ZipOutputStream zip;
    private final Set<String> archivosEscritos = new HashSet<>();
    private final Set<String> carpetas = new HashSet<>();
    private final List<Archivo> archivos = new ArrayList<>();
    private Medidor abierto;
    private String rutaAbierta;
    private boolean terminado;

    PaqueteZip(OutputStream destino, JsonMapper json) {
        this.json = json;
        this.total = new Medidor(destino);
        this.zip = new ZipOutputStream(total, StandardCharsets.UTF_8);
    }

    /** Un archivo del paquete como lo lista el manifiesto. */
    record Archivo(String ruta, long tamano, String sha256) {
    }

    /** El ZIP terminado: cuantos archivos lleva sin contar el manifiesto, cuanto pesa y su SHA-256. */
    record Resumen(int archivos, long tamano, String sha256) {
    }

    @Override
    public void json(String ruta, Object valor) {
        escribir(ruta, json.writerWithDefaultPrettyPrinter().writeValueAsBytes(valor));
    }

    @Override
    public void texto(String ruta, String contenido) {
        escribir(ruta, contenido.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void copiar(String ruta, InputStream contenido) {
        OutputStream salida = abrir(ruta, false);
        try {
            contenido.transferTo(salida);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo copiar " + ruta + " al paquete", e);
        }
        cerrar();
    }

    @Override
    public FilasJson filasJson(String ruta) {
        OutputStream salida = abrir(ruta, false);
        escribirEn(salida, APERTURA);
        return new FilasJson() {
            private boolean primera = true;

            @Override
            public void agregar(Object fila) {
                comprobarAbierto(salida);
                escribirEn(salida, primera ? SALTO : SEPARADOR);
                escribirEn(salida, json.writeValueAsBytes(fila));
                primera = false;
            }

            @Override
            public void close() {
                if (abierto == salida) {
                    escribirEn(salida, CIERRE);
                    cerrar();
                }
            }
        };
    }

    @Override
    public FilasCsv filasCsv(String ruta, List<String> cabecera) {
        if (cabecera.isEmpty()) {
            throw new IllegalArgumentException("Un CSV sin columnas no dice nada: " + ruta);
        }
        OutputStream salida = abrir(ruta, false);
        escribirEn(salida, linea(cabecera));
        return new FilasCsv() {
            @Override
            public void agregar(List<?> celdas) {
                comprobarAbierto(salida);
                if (celdas.size() != cabecera.size()) {
                    throw new IllegalArgumentException("La fila de " + ruta + " tiene " + celdas.size()
                            + " celdas y la cabecera " + cabecera.size() + ".");
                }
                escribirEn(salida, linea(celdas));
            }

            @Override
            public void close() {
                if (abierto == salida) {
                    cerrar();
                }
            }
        };
    }

    /**
     * Escribe el manifiesto, con lo que dice la cabecera y la lista de archivos, y termina el ZIP. Despues no se puede
     * agregar nada.
     */
    Resumen terminar(Map<String, Object> cabecera) {
        Map<String, Object> manifiesto = new LinkedHashMap<>(cabecera);
        manifiesto.put("archivos", archivos.stream().map(archivo -> {
            Map<String, Object> linea = new LinkedHashMap<>();
            linea.put("ruta", archivo.ruta());
            linea.put("tamano", archivo.tamano());
            linea.put("sha256", archivo.sha256());
            return linea;
        }).toList());
        OutputStream salida = abrir(MANIFIESTO, true);
        escribirEn(salida, json.writerWithDefaultPrettyPrinter().writeValueAsBytes(manifiesto));
        cerrar();
        try {
            zip.finish();
            zip.flush();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo terminar el paquete", e);
        }
        terminado = true;
        return new Resumen(archivos.size() - 1, total.bytes(), total.sha256());
    }

    @Override
    public void close() {
        try {
            zip.close();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo cerrar el paquete", e);
        }
    }

    private static byte[] linea(List<?> celdas) {
        return celdas.stream().map(CeldaCsv::de)
                .collect(Collectors.joining(",", "", FIN_DE_LINEA_CSV)).getBytes(StandardCharsets.UTF_8);
    }

    private void escribir(String ruta, byte[] contenido) {
        escribirEn(abrir(ruta, false), contenido);
        cerrar();
    }

    private OutputStream abrir(String ruta, boolean manifiesto) {
        if (terminado) {
            throw new IllegalStateException("El paquete ya se termino: no se le agrega " + ruta + ".");
        }
        if (abierto != null) {
            throw new IllegalStateException("No se puede empezar " + ruta + " con " + rutaAbierta + " abierto.");
        }
        comprobar(ruta, manifiesto);
        try {
            zip.putNextEntry(new ZipEntry(ruta));
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo empezar " + ruta + " en el paquete", e);
        }
        abierto = new Medidor(zip);
        rutaAbierta = ruta;
        return abierto;
    }

    private void cerrar() {
        try {
            zip.closeEntry();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo cerrar " + rutaAbierta + " en el paquete", e);
        }
        archivos.add(new Archivo(rutaAbierta, abierto.bytes(), abierto.sha256()));
        abierto = null;
        rutaAbierta = null;
    }

    private void comprobarAbierto(OutputStream salida) {
        if (abierto != salida) {
            throw new IllegalStateException("Ese archivo del paquete ya se cerro.");
        }
    }

    /**
     * Una ruta relativa de tramos validos, que no es el manifiesto, que no se repite y que no choca con una carpeta: ni
     * un archivo donde ya hay una carpeta, ni una carpeta donde ya hay un archivo.
     */
    private void comprobar(String ruta, boolean manifiesto) {
        if (ruta == null || ruta.isEmpty() || ruta.length() > LARGO_MAXIMO_DE_RUTA) {
            throw new IllegalArgumentException("Ruta del paquete vacia o demasiado larga: " + ruta);
        }
        if (!manifiesto && MANIFIESTO.equals(ruta)) {
            throw new IllegalArgumentException(LA_RUTA + MANIFIESTO + " es la del manifiesto.");
        }
        String[] tramos = ruta.split("/", -1);
        StringBuilder carpeta = new StringBuilder();
        for (int i = 0; i < tramos.length; i++) {
            if (!Tramos.esValido(tramos[i])) {
                throw new IllegalArgumentException("Ruta del paquete no valida: " + ruta);
            }
            if (i < tramos.length - 1) {
                carpeta.append(tramos[i]);
                if (archivosEscritos.contains(carpeta.toString())) {
                    throw new IllegalArgumentException(LA_RUTA + ruta + " pasa por el archivo " + carpeta + ".");
                }
                carpeta.append('/');
            }
        }
        if (carpetas.contains(ruta) || !archivosEscritos.add(ruta)) {
            throw new IllegalArgumentException(LA_RUTA + ruta + " ya esta en el paquete.");
        }
        int barra = ruta.lastIndexOf('/');
        while (barra > 0) {
            carpetas.add(ruta.substring(0, barra));
            barra = ruta.lastIndexOf('/', barra - 1);
        }
    }

    private static void escribirEn(OutputStream salida, byte[] contenido) {
        try {
            salida.write(contenido);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo escribir en el paquete", e);
        }
    }

    /**
     * Lo que pasa por aqui sigue de largo y queda contado y en el SHA-256. Cerrarlo no cierra lo de abajo: las partes
     * pueden cerrar lo que escriben sin cerrar el ZIP. Cada medidor lleva su propio resumen, que solo usa quien escribe
     * en el.
     */
    static final class Medidor extends DigestOutputStream {

        private long bytes;

        Medidor(OutputStream destino) {
            super(destino, sha256Nuevo());
        }

        private static MessageDigest sha256Nuevo() {
            try {
                return MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException("La JVM no trae SHA-256.", e);
            }
        }

        @Override
        public void write(int octeto) throws IOException {
            super.write(octeto);
            bytes++;
        }

        @Override
        public void write(byte[] datos, int desde, int largo) throws IOException {
            super.write(datos, desde, largo);
            bytes += largo;
        }

        @Override
        public void close() {
            // Lo de abajo es de quien lo abrio
        }

        long bytes() {
            return bytes;
        }

        String sha256() {
            return HexFormat.of().formatHex(getMessageDigest().digest());
        }
    }
}
