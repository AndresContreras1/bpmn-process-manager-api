package com.facimus.procesos.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * SHA-256 en hexadecimal. Lo usan tres cosas distintas por la misma razon: comparar dos contenidos sin guardarlos
 * enteros ni volver a leerlos. El token de refresco se guarda solo como huella, la clave de idempotencia compara la
 * peticion repetida con la original, y una version publicada compara su diagrama con el borrador de hoy.
 */
public final class Huella {

    private Huella() {
    }

    public static String de(String texto) {
        return de(texto.getBytes(StandardCharsets.UTF_8));
    }

    /** Las partes se encadenan en el orden recibido: la huella cambia si cambia cualquiera de ellas o su orden. */
    public static String de(byte[]... partes) {
        MessageDigest sha = sha256();
        for (byte[] parte : partes) {
            sha.update(parte);
        }
        return HexFormat.of().formatHex(sha.digest());
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("La JVM no trae SHA-256.", e);
        }
    }
}
