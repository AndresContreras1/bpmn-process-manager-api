package com.facimus.procesos.gestion.service.impl;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * PR 49: lo que el segundo factor guarda y no puede quedar en claro en la base. Con {@code MFA_CLAVE_DE_CIFRADO} se
 * derivan dos claves, una por uso, para que ninguna sirva para lo de la otra:
 * <ul>
 * <li>AES-256-GCM para el secreto del TOTP, con un nonce aleatorio de 96 bits por cifrado y el id del usuario como
 * dato asociado: un secreto copiado a la fila de otra persona no se descifra;</li>
 * <li>HMAC-SHA256 para los codigos de recuperacion. Tienen 50 bits, y un SHA-256 a secas se recorreria entero con una
 * copia de la tabla; sin la clave, que no esta en la base, no.</li>
 * </ul>
 */
@Component
public class CifradoDeSecretos {

    /** Lo minimo para la clave de la que salen las otras: como la del JWT. */
    static final int LARGO_MINIMO = 32;

    private static final String HMAC = "HmacSHA256";
    private static final String AES = "AES/GCM/NoPadding";
    private static final int BYTES_DEL_NONCE = 12;
    private static final int BITS_DE_LA_ETIQUETA = 128;

    private final SecretKey claveDeCifrado;
    private final SecretKey claveDeHuellas;
    private final SecureRandom aleatorio = new SecureRandom();

    public CifradoDeSecretos(@Value("${mfa.clave-de-cifrado}") String clave) {
        if (clave == null || clave.length() < LARGO_MINIMO) {
            throw new IllegalStateException("MFA_CLAVE_DE_CIFRADO tiene que tener al menos " + LARGO_MINIMO
                    + " caracteres.");
        }
        byte[] base = clave.getBytes(StandardCharsets.UTF_8);
        this.claveDeCifrado = new SecretKeySpec(hmac(base, "bpmn-process-manager/mfa/aes-gcm"), "AES");
        this.claveDeHuellas = new SecretKeySpec(hmac(base, "bpmn-process-manager/mfa/codigos"), HMAC);
    }

    /** El secreto cifrado en Base64: el nonce y despues el texto cifrado con su etiqueta. */
    public String cifrar(byte[] secreto, Long usuarioId) {
        byte[] nonce = new byte[BYTES_DEL_NONCE];
        aleatorio.nextBytes(nonce);
        try {
            Cipher cifrador = Cipher.getInstance(AES);
            cifrador.init(Cipher.ENCRYPT_MODE, claveDeCifrado, new GCMParameterSpec(BITS_DE_LA_ETIQUETA, nonce));
            cifrador.updateAAD(asociado(usuarioId));
            ByteArrayOutputStream guardado = new ByteArrayOutputStream();
            guardado.writeBytes(nonce);
            guardado.writeBytes(cifrador.doFinal(secreto));
            return Base64.getEncoder().encodeToString(guardado.toByteArray());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("AES-GCM tiene que existir en toda JVM.", e);
        }
    }

    /** Lanza IllegalStateException si no lo cifro esta clave para este usuario o si alguien lo altero. */
    public byte[] descifrar(String guardado, Long usuarioId) {
        byte[] datos = Base64.getDecoder().decode(guardado);
        try {
            Cipher cifrador = Cipher.getInstance(AES);
            cifrador.init(Cipher.DECRYPT_MODE, claveDeCifrado,
                    new GCMParameterSpec(BITS_DE_LA_ETIQUETA, datos, 0, BYTES_DEL_NONCE));
            cifrador.updateAAD(asociado(usuarioId));
            return cifrador.doFinal(datos, BYTES_DEL_NONCE, datos.length - BYTES_DEL_NONCE);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("El secreto del segundo factor no se pudo descifrar.", e);
        }
    }

    /** La huella de un codigo de recuperacion: HMAC-SHA256 en hexadecimal, 64 caracteres. */
    public String huella(String codigo) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(claveDeHuellas);
            return HexFormat.of().formatHex(mac.doFinal(codigo.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 tiene que existir en toda JVM.", e);
        }
    }

    private static byte[] asociado(Long usuarioId) {
        return ("usuario:" + usuarioId).getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] hmac(byte[] clave, String proposito) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(clave, HMAC));
            return mac.doFinal(proposito.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 tiene que existir en toda JVM.", e);
        }
    }
}
