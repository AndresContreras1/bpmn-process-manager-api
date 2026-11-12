package com.facimus.procesos.common.almacen;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * PR 61: enlaces que valen por si mismos durante unos minutos, como las URL prefirmadas de S3, pero servidos por la
 * API: el almacen nunca queda a la vista del navegador. La firma es un HMAC-SHA256 de lo que se descarga y de cuando
 * vence; quien cambia cualquiera de las dos cosas rompe la firma. La clave se deriva del secreto de la API con una
 * etiqueta propia, asi que un token de acceso y un enlace nunca se pueden usar uno por otro; sin secreto (en
 * desarrollo), es una clave al azar de esta instancia.
 */
public class EnlacesFirmados {

    private static final SecureRandom AZAR = new SecureRandom();

    private static final String HMAC = "HmacSHA256";
    private static final Base64.Encoder BASE64 = Base64.getUrlEncoder().withoutPadding();

    private final byte[] clave;
    private final Clock reloj;

    public EnlacesFirmados(String secreto, Clock reloj) {
        this.reloj = reloj;
        byte[] semilla = secreto == null || secreto.isBlank() ? aleatoria() : secreto.getBytes(StandardCharsets.UTF_8);
        this.clave = hmac(semilla, "bpmn-process-manager/descargas/v1".getBytes(StandardCharsets.UTF_8));
    }

    /** Hasta cuando vale un enlace hecho ahora con esa vida. */
    public Instant vencimiento(Duration vida) {
        return reloj.instant().plus(vida).truncatedTo(ChronoUnit.SECONDS);
    }

    /** La firma de un recurso hasta un momento, en base64url. */
    public String firmar(String recurso, Instant vence) {
        return BASE64.encodeToString(hmac(clave, mensaje(recurso, vence)));
    }

    /** Si la firma es la de ese recurso y ese vencimiento, y todavia no vencio. Compara sin filtrar el tiempo. */
    public boolean vale(String recurso, Instant vence, String firma) {
        if (firma == null || vence == null || reloj.instant().isAfter(vence)) {
            return false;
        }
        byte[] esperada = firmar(recurso, vence).getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(esperada, firma.getBytes(StandardCharsets.US_ASCII));
    }

    private static byte[] mensaje(String recurso, Instant vence) {
        return (recurso + "\n" + vence.getEpochSecond()).getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] hmac(byte[] clave, byte[] mensaje) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(clave, HMAC));
            return mac.doFinal(mensaje);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("La JVM no tiene HmacSHA256", e);
        }
    }

    private static byte[] aleatoria() {
        byte[] bytes = new byte[32];
        AZAR.nextBytes(bytes);
        return bytes;
    }
}
