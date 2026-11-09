package com.facimus.procesos.common.security;

import java.io.ByteArrayOutputStream;
import java.util.Locale;

/**
 * PR 49: Base32 del RFC 4648, sin relleno, que es como las aplicaciones de autenticacion esperan el secreto del TOTP
 * en el codigo QR y al escribirlo a mano. Al leer ignora mayusculas, espacios y guiones.
 */
public final class Base32 {

    private static final String ALFABETO = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final int BITS_POR_CARACTER = 5;

    private Base32() {
    }

    public static String codificar(byte[] datos) {
        StringBuilder texto = new StringBuilder((datos.length * 8 + 4) / BITS_POR_CARACTER);
        int acumulado = 0;
        int bits = 0;
        for (byte dato : datos) {
            acumulado = (acumulado << 8) | (dato & 0xff);
            bits += 8;
            while (bits >= BITS_POR_CARACTER) {
                bits -= BITS_POR_CARACTER;
                texto.append(ALFABETO.charAt((acumulado >> bits) & 0x1f));
            }
        }
        if (bits > 0) {
            texto.append(ALFABETO.charAt((acumulado << (BITS_POR_CARACTER - bits)) & 0x1f));
        }
        return texto.toString();
    }

    /** Lanza IllegalArgumentException si trae un caracter que no es del alfabeto. */
    public static byte[] decodificar(String texto) {
        String limpio = texto.replace(" ", "").replace("-", "").replace("=", "").toUpperCase(Locale.ROOT);
        ByteArrayOutputStream datos = new ByteArrayOutputStream(limpio.length() * BITS_POR_CARACTER / 8);
        int acumulado = 0;
        int bits = 0;
        for (char caracter : limpio.toCharArray()) {
            int valor = ALFABETO.indexOf(caracter);
            if (valor < 0) {
                throw new IllegalArgumentException("No es Base32: " + caracter);
            }
            acumulado = (acumulado << BITS_POR_CARACTER) | valor;
            bits += BITS_POR_CARACTER;
            if (bits >= 8) {
                bits -= 8;
                datos.write((acumulado >> bits) & 0xff);
            }
        }
        return datos.toByteArray();
    }
}
