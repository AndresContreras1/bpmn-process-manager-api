package com.facimus.procesos.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.virtualauthenticator.HasVirtualAuthenticator;
import org.openqa.selenium.virtualauthenticator.VirtualAuthenticator;
import org.openqa.selenium.virtualauthenticator.VirtualAuthenticatorOptions;

/**
 * F14: la seguridad de la cuenta desde la web. Una passkey de verdad, con el autenticador virtual de Chrome, que
 * despues abre la sesion sin clave y completa el segundo factor de la clave; y la aplicacion de autenticacion, con
 * el codigo calculado aqui a partir de su clave, y un codigo de recuperacion para entrar.
 */
class SeguridadDeLaCuentaTest extends PruebaE2E {

    @Test
    @DisplayName("una passkey registrada en la cuenta abre la sesion sin clave, y tambien completa el login con clave")
    void passkeyDeLaCuenta() {
        VirtualAuthenticator autenticador = ((HasVirtualAuthenticator) navegador).addVirtualAuthenticator(
                new VirtualAuthenticatorOptions()
                        .setProtocol(VirtualAuthenticatorOptions.Protocol.CTAP2)
                        .setTransport(VirtualAuthenticatorOptions.Transport.INTERNAL)
                        .setHasResidentKey(true)
                        .setHasUserVerification(true)
                        .setIsUserVerified(true));
        ApiDeDatos.Tienda tienda = api.registrarTienda("Tienda Passkey", nitDe("passkey"), correoDe("passkey"),
                "huellas-en-la-arena");
        entrar(tienda);

        ir("/cuenta/seguridad");
        escribir(Paginas.Seguridad.ETIQUETA_PASSKEY, "Portatil de pruebas");
        pulsable(Paginas.Seguridad.REGISTRAR_PASSKEY).click();
        esperarTexto(Paginas.Seguridad.PASSKEY, "Portatil de pruebas");
        // La primera passkey trae los codigos de recuperacion, que se ven una sola vez
        esperarFilas(Paginas.Seguridad.CODIGO_DE_RECUPERACION, 10);
        assertEquals(1, autenticador.getCredentials().size(), "el autenticador guardo la passkey");

        // Sin clave: la passkey dice quien es
        salir();
        ir("/login");
        pulsable(Paginas.Login.CON_PASSKEY).click();
        esperarUrl("/procesos");
        visible(Paginas.Sesion.USUARIO);

        // Con clave: la passkey es ahora su segundo factor, y la completa
        salir();
        ir("/login");
        escribir(Paginas.Login.EMAIL, tienda.correo());
        escribir(Paginas.Login.CLAVE, tienda.clave());
        pulsable(Paginas.Login.ENTRAR).click();
        visible(Paginas.Login.CODIGO);
        pulsable(Paginas.Login.SEGUNDO_CON_PASSKEY).click();
        esperarUrl("/procesos");
        visible(Paginas.Sesion.USUARIO);
    }

    @Test
    @DisplayName("la aplicacion de autenticacion se activa con su primer codigo, y despues el login pide un codigo, que "
            + "puede ser uno de recuperacion; la cuenta lista la sesion abierta")
    void aplicacionDeAutenticacion() throws GeneralSecurityException {
        ApiDeDatos.Tienda tienda = api.registrarTienda("Tienda Codigos", nitDe("codigos"), correoDe("codigos"),
                "relojes-de-arena-fina");
        entrar(tienda);

        ir("/cuenta/seguridad");
        pulsable(Paginas.Seguridad.EMPEZAR_TOTP).click();
        visible(Paginas.Seguridad.QR);
        String secreto = textoDe(Paginas.Seguridad.SECRETO);
        escribir(Paginas.Seguridad.CODIGO_TOTP, codigo(secreto, Instant.now()));
        pulsable(Paginas.Seguridad.CONFIRMAR_TOTP).click();
        visible(Paginas.Seguridad.TOTP_ACTIVO);
        esperarFilas(Paginas.Seguridad.CODIGO_DE_RECUPERACION, 10);
        String recuperacion = navegador.findElements(Paginas.Seguridad.CODIGO_DE_RECUPERACION).get(0).getText();
        // La de este navegador y la que abrio la API de datos al preparar la prueba: una sola es la actual
        List<WebElement> sesiones = navegador.findElements(Paginas.Seguridad.SESION);
        assertEquals(2, sesiones.size(), "las dos sesiones abiertas de la persona");
        assertEquals(1, navegador.findElements(By.cssSelector("[data-testid='sesion-actual']")).size(),
                "y solo la de este navegador va marcada como la actual");

        salir();
        ir("/login");
        escribir(Paginas.Login.EMAIL, tienda.correo());
        escribir(Paginas.Login.CLAVE, tienda.clave());
        pulsable(Paginas.Login.ENTRAR).click();
        // La clave sola ya no abre la cuenta
        escribir(Paginas.Login.CODIGO, "000000");
        pulsable(Paginas.Login.ENVIAR_CODIGO).click();
        visible(Paginas.Login.ERROR);
        assertFalse(navegador.getCurrentUrl().contains("/procesos"), "un codigo malo no deja entrar");

        escribir(Paginas.Login.CODIGO, recuperacion);
        pulsable(Paginas.Login.ENVIAR_CODIGO).click();
        esperarUrl("/procesos");

        ir("/cuenta/seguridad");
        esperarTexto(Paginas.Seguridad.CODIGOS_RESTANTES, "9");
    }

    /** RFC 6238 con SHA-1, seis digitos y pasos de treinta segundos, como la aplicacion del telefono. */
    private static String codigo(String secretoBase32, Instant momento) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(base32(secretoBase32), "HmacSHA1"));
        byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(momento.getEpochSecond() / 30).array());
        int desde = hash[hash.length - 1] & 0x0f;
        int numero = ((hash[desde] & 0x7f) << 24) | ((hash[desde + 1] & 0xff) << 16) | ((hash[desde + 2] & 0xff) << 8)
                | (hash[desde + 3] & 0xff);
        return String.format("%06d", numero % 1_000_000);
    }

    private static byte[] base32(String texto) {
        String alfabeto = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
        String limpio = texto.replace("=", "").replace(" ", "").toUpperCase(Locale.ROOT);
        ByteBuffer bytes = ByteBuffer.allocate(limpio.length() * 5 / 8);
        int buffer = 0;
        int bits = 0;
        for (char letra : limpio.toCharArray()) {
            buffer = (buffer << 5) | alfabeto.indexOf(letra);
            bits += 5;
            if (bits >= 8) {
                bytes.put((byte) (buffer >> (bits - 8)));
                bits -= 8;
            }
        }
        return bytes.array();
    }
}
