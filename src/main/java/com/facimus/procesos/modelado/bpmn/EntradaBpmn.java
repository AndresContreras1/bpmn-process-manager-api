package com.facimus.procesos.modelado.bpmn;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import com.facimus.procesos.common.xml.LimitesXml;
import com.facimus.procesos.common.xml.SecureXml;
import com.facimus.procesos.common.xml.XmlRechazadoException;

/**
 * PR 54: un archivo BPMN tal como llega, listo para {@link SecureXml}. Muchas herramientas, y la suite de referencia de
 * la OMG, escriben en ISO-8859-1; {@code SecureXml} solo deja pasar UTF-8 y UTF-16 (PR 52), y esa regla se queda. Lo
 * que se hace aqui es pasar a UTF-8 los archivos declarados en una codificacion de un byte compatible con ASCII: en
 * ellas todo byte por debajo de 0x80 es ASCII, asi que no hay forma de esconder un DOCTYPE, y despues el archivo entra
 * por la misma puerta que cualquier otro. UTF-7, EBCDIC y el resto siguen rechazados.
 */
public final class EntradaBpmn {

    private static final Map<String, Charset> DE_UN_BYTE = Map.of(
            "ISO-8859-1", StandardCharsets.ISO_8859_1,
            "LATIN1", StandardCharsets.ISO_8859_1,
            "ISO-8859-15", Charset.forName("ISO-8859-15"),
            "WINDOWS-1252", Charset.forName("windows-1252"),
            "CP1252", Charset.forName("windows-1252"),
            "US-ASCII", StandardCharsets.US_ASCII,
            "ASCII", StandardCharsets.US_ASCII);


    private EntradaBpmn() {
    }

    /**
     * @param bytes el archivo ya comprobado por {@link SecureXml}, en UTF-8 o UTF-16
     * @param recibidos los bytes que llegaron, para su huella
     * @param codificacion la de un byte en la que venia, o nula si ya venia en UTF
     */
    public record Leida(byte[] bytes, byte[] recibidos, String codificacion) {

        @Override
        public boolean equals(Object otro) {
            return otro instanceof Leida leida && Arrays.equals(bytes, leida.bytes)
                    && Arrays.equals(recibidos, leida.recibidos) && Objects.equals(codificacion, leida.codificacion);
        }

        @Override
        public int hashCode() {
            return Objects.hash(Arrays.hashCode(bytes), Arrays.hashCode(recibidos), codificacion);
        }

        /** Sin el contenido: un archivo entero no cabe en un log. */
        @Override
        public String toString() {
            return "Leida[" + bytes.length + " bytes, codificacion=" + codificacion + "]";
        }
    }

    public static Leida leer(InputStream entrada, LimitesXml limites) {
        byte[] recibidos;
        try {
            recibidos = entrada.readNBytes((int) Math.min(Integer.MAX_VALUE - 8L, limites.bytes() + 1));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (recibidos.length > limites.bytes()) {
            throw new XmlRechazadoException("El XML pasa de " + limites.bytes() + " bytes.");
        }
        String cabeza = new String(recibidos, 0, Math.min(recibidos.length, 256), StandardCharsets.ISO_8859_1);
        int[] nombre = codificacionDeclarada(cabeza);
        String codificacion = null;
        byte[] utf8 = recibidos;
        if (nombre != null) {
            String declarada = cabeza.substring(nombre[0], nombre[1]);
            Charset deUnByte = DE_UN_BYTE.get(declarada.toUpperCase(Locale.ROOT));
            if (deUnByte != null) {
                codificacion = declarada;
                // En una codificacion de un byte, la posicion de cada caracter es la de su byte
                String texto = new String(recibidos, deUnByte);
                utf8 = (texto.substring(0, nombre[0]) + "UTF-8" + texto.substring(nombre[1]))
                        .getBytes(StandardCharsets.UTF_8);
            }
        }
        return new Leida(SecureXml.comprobar(new ByteArrayInputStream(utf8), limites), recibidos, codificacion);
    }

    /**
     * Donde empieza y termina el nombre de la codificacion en la declaracion XML, o nulo si el archivo no empieza
     * con una o no la dice. Se lee a mano, caracter por caracter, sobre los primeros bytes.
     */
    static int[] codificacionDeclarada(String cabeza) {
        if (!cabeza.startsWith("<?xml")) {
            return null;
        }
        int fin = cabeza.indexOf("?>");
        String declaracion = fin < 0 ? cabeza : cabeza.substring(0, fin);
        int encoding = declaracion.indexOf("encoding");
        if (encoding < 0 || !Character.isWhitespace(declaracion.charAt(encoding - 1))) {
            return null;
        }
        int i = encoding + "encoding".length();
        while (i < declaracion.length() && Character.isWhitespace(declaracion.charAt(i))) {
            i++;
        }
        if (i >= declaracion.length() || declaracion.charAt(i) != '=') {
            return null;
        }
        i++;
        while (i < declaracion.length() && Character.isWhitespace(declaracion.charAt(i))) {
            i++;
        }
        if (i >= declaracion.length() || declaracion.charAt(i) != '"' && declaracion.charAt(i) != '\'') {
            return null;
        }
        int cierre = declaracion.indexOf(declaracion.charAt(i), i + 1);
        return cierre < 0 ? null : new int[] {i + 1, cierre};
    }
}
