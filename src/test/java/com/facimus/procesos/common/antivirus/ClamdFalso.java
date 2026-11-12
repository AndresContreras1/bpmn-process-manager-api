package com.facimus.procesos.common.antivirus;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * PR 61: un clamd de mentira que habla el protocolo de verdad: lee {@code zINSTREAM}, los trozos con su largo y el
 * trozo vacio del final, y contesta {@code FOUND} si el archivo trae la cadena de prueba EICAR. Con
 * {@link #contestar(String)} se le puede hacer decir cualquier otra cosa, o nada.
 */
public final class ClamdFalso implements AutoCloseable {

    /** La cadena de prueba de EICAR, que cualquier antivirus da por virus sin que lo sea. */
    public static final String EICAR = "X5O!P%@AP[4\\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*";

    /**
     * Lo que este clamd da por virus ademas de EICAR. Las pruebas que escriben el archivo en disco usan esta marca: el
     * antivirus de un Windows (el de desarrollo, el runner del CI) pone EICAR en cuarentena antes de que la API lo lea.
     */
    public static final String VIRUS = "MARCA-DE-VIRUS-DE-LAS-PRUEBAS-DE-DOCUMENTOS";

    private final ServerSocket servidor;
    private final AtomicInteger revisados = new AtomicInteger();
    private volatile String respuestaFija;

    private ClamdFalso(ServerSocket servidor) {
        this.servidor = servidor;
    }

    public static ClamdFalso arrancar() {
        try {
            ClamdFalso clamd = new ClamdFalso(new ServerSocket(0, 50, InetAddress.getLoopbackAddress()));
            Thread hilo = new Thread(clamd::atender, "clamd-falso");
            hilo.setDaemon(true);
            hilo.start();
            return clamd;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public int puerto() {
        return servidor.getLocalPort();
    }

    public int revisados() {
        return revisados.get();
    }

    /** Lo que contesta de aqui en adelante, en vez de revisar; {@code null} vuelve a revisar, "" no contesta nada. */
    public void contestar(String respuesta) {
        this.respuestaFija = respuesta;
    }

    private void atender() {
        while (!servidor.isClosed()) {
            try (Socket socket = servidor.accept()) {
                responder(socket);
            } catch (IOException _) {
                // El servidor se cerro, o el cliente se fue a mitad: el siguiente sigue
            }
        }
    }

    private void responder(Socket socket) throws IOException {
        DataInputStream entrada = new DataInputStream(socket.getInputStream());
        byte[] comando = entrada.readNBytes("zINSTREAM\0".length());
        if (!"zINSTREAM\0".equals(new String(comando, StandardCharsets.US_ASCII))) {
            escribir(socket.getOutputStream(), "UNKNOWN COMMAND");
            return;
        }
        ByteArrayOutputStream contenido = new ByteArrayOutputStream();
        int largo;
        while ((largo = entrada.readInt()) > 0) {
            contenido.write(entrada.readNBytes(largo));
        }
        revisados.incrementAndGet();
        String fija = respuestaFija;
        if (fija != null) {
            if (!fija.isEmpty()) {
                escribir(socket.getOutputStream(), fija);
            }
            return;
        }
        String leido = contenido.toString(StandardCharsets.ISO_8859_1);
        String respuesta = "stream: OK";
        if (leido.contains(EICAR)) {
            respuesta = "stream: Eicar-Test-Signature FOUND";
        } else if (leido.contains(VIRUS)) {
            respuesta = "stream: Prueba.Marca-De-Virus FOUND";
        }
        escribir(socket.getOutputStream(), respuesta);
    }

    private static void escribir(OutputStream salida, String respuesta) throws IOException {
        salida.write((respuesta + "\0").getBytes(StandardCharsets.US_ASCII));
        salida.flush();
    }

    @Override
    public void close() throws IOException {
        servidor.close();
    }
}
