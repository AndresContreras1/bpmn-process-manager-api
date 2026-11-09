package com.facimus.procesos.common.security;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.OptionalLong;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * PR 49: contrasenas de un solo uso por tiempo (RFC 6238 sobre el HOTP del RFC 4226), las que muestra una aplicacion
 * de autenticacion: HMAC-SHA1, pasos de 30 segundos desde la epoca Unix y 6 digitos, como las esperan todas.
 *
 * <p>Se acepta el paso de ahora y uno a cada lado, para la deriva del reloj del telefono. El que llama guarda el
 * ultimo paso aceptado y no acepta otro igual o anterior: un codigo visto por encima del hombro ya no sirve.
 */
public final class Totp {

    /** Lo que dura un codigo. */
    public static final long SEGUNDOS_POR_PASO = 30;

    /** Los digitos que muestran las aplicaciones. */
    public static final int DIGITOS = 6;

    /** Los pasos que se aceptan a cada lado del de ahora. */
    static final int VENTANA = 1;

    private static final String ALGORITMO = "HmacSHA1";

    private Totp() {
    }

    /** El paso de 30 segundos al que pertenece un instante. */
    public static long paso(Instant instante) {
        return Math.floorDiv(instante.getEpochSecond(), SEGUNDOS_POR_PASO);
    }

    /** El codigo de un paso, con los digitos que se pidan: 6 en la API, 8 en los vectores del RFC. */
    public static String codigo(byte[] secreto, long paso, int digitos) {
        byte[] hash = hmac(secreto, ByteBuffer.allocate(Long.BYTES).putLong(paso).array());
        // Truncado dinamico (RFC 4226, 5.3): los 4 bits bajos del ultimo byte dicen de donde leer 31 bits.
        int desde = hash[hash.length - 1] & 0x0f;
        int binario = ((hash[desde] & 0x7f) << 24) | ((hash[desde + 1] & 0xff) << 16)
                | ((hash[desde + 2] & 0xff) << 8) | (hash[desde + 3] & 0xff);
        int modulo = (int) Math.pow(10, digitos);
        return String.format("%0" + digitos + "d", binario % modulo);
    }

    /**
     * El paso en el que el codigo es valido, dentro de la ventana alrededor de ahora y despues del ultimo usado; vacio
     * si no lo es en ninguno. Compara en tiempo constante, para que la demora no diga cuantos digitos acertaron.
     */
    public static OptionalLong verificar(byte[] secreto, String codigo, Instant ahora, long ultimoUsado) {
        if (codigo == null || codigo.length() != DIGITOS || !codigo.chars().allMatch(Character::isDigit)) {
            return OptionalLong.empty();
        }
        long actual = paso(ahora);
        for (long paso = actual - VENTANA; paso <= actual + VENTANA; paso++) {
            if (paso > ultimoUsado && MessageDigest.isEqual(
                    codigo(secreto, paso, DIGITOS).getBytes(StandardCharsets.US_ASCII),
                    codigo.getBytes(StandardCharsets.US_ASCII))) {
                return OptionalLong.of(paso);
            }
        }
        return OptionalLong.empty();
    }

    private static byte[] hmac(byte[] clave, byte[] mensaje) {
        try {
            Mac mac = Mac.getInstance(ALGORITMO);
            mac.init(new SecretKeySpec(clave, ALGORITMO));
            return mac.doFinal(mensaje);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA1 tiene que existir en toda JVM.", e);
        }
    }
}
