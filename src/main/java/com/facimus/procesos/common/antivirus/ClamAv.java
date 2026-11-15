package com.facimus.procesos.common.antivirus;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * PR 61: ClamAV por su demonio, {@code clamd}, con el comando {@code INSTREAM} de su protocolo TCP: el archivo viaja en
 * trozos con su largo adelante, y un trozo de largo cero dice que termino. clamd contesta {@code stream: OK} si esta
 * limpio y {@code stream: <firma> FOUND} si no. Cualquier otra respuesta, o ninguna a tiempo, es un error: el
 * documento no queda disponible por una falla del antivirus.
 */
public class ClamAv implements Antivirus {

    private static final int TROZO = 8192;
    private static final String LIMPIO = "stream: OK";
    private static final String ENCONTRADO = " FOUND";

    private final String host;
    private final int puerto;
    private final Duration espera;

    public ClamAv(String host, int puerto, Duration espera) {
        this.host = host;
        this.puerto = puerto;
        this.espera = espera;
    }

    @Override
    public boolean disponible() {
        return true;
    }

    @Override
    public ResultadoDelAntivirus revisar(InputStream contenido) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, puerto), (int) espera.toMillis());
            socket.setSoTimeout((int) espera.toMillis());
            DataOutputStream salida = new DataOutputStream(socket.getOutputStream());
            salida.write("zINSTREAM\0".getBytes(StandardCharsets.US_ASCII));
            enviar(contenido, salida);
            salida.writeInt(0);
            salida.flush();
            String respuesta = leer(socket.getInputStream());
            if (respuesta.equals(LIMPIO)) {
                return ResultadoDelAntivirus.sinHallazgos();
            }
            if (respuesta.startsWith("stream: ") && respuesta.endsWith(ENCONTRADO)) {
                return ResultadoDelAntivirus.infectado(respuesta.substring("stream: ".length(),
                        respuesta.length() - ENCONTRADO.length()));
            }
            throw new IllegalStateException("ClamAV contesto algo que no se entiende: " + respuesta);
        } catch (IOException e) {
            throw new UncheckedIOException("ClamAV no contesto en " + host + ":" + puerto, e);
        }
    }

    private static void enviar(InputStream contenido, DataOutputStream salida) throws IOException {
        byte[] trozo = new byte[TROZO];
        int leidos;
        while ((leidos = contenido.readNBytes(trozo, 0, TROZO)) > 0) {
            salida.writeInt(leidos);
            salida.write(trozo, 0, leidos);
        }
    }

    /** La respuesta de clamd termina en un caracter nulo; no hace falta mas de una linea. */
    private static String leer(InputStream entrada) throws IOException {
        byte[] respuesta = entrada.readNBytes(1024);
        String texto = new String(respuesta, StandardCharsets.US_ASCII);
        int fin = texto.indexOf('\0');
        return (fin >= 0 ? texto.substring(0, fin) : texto).strip();
    }
}
