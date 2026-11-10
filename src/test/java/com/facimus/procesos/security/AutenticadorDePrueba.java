package com.facimus.procesos.security;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * PR 49b: un autenticador de WebAuthn para las pruebas, que hace lo que hacen un navegador y un dispositivo: crea un par
 * de claves P-256, arma la atestacion "none" en CBOR y firma lo que pide la API, y devuelve la credencial en la forma de
 * {@code PublicKeyCredential.toJSON()}. Lo que hace un dispositivo de verdad, sin nada de la biblioteca que lo verifica
 * del otro lado: si la API acepta lo que este firma, acepta lo de un navegador.
 *
 * <p>Cada metodo deja torcer una cosa a la vez (el origen, la verificacion de la persona, el contador, la firma) para
 * probar que la API la rechaza.
 */
final class AutenticadorDePrueba {

    static final String ORIGEN = "http://localhost:4200";

    private static final byte BANDERA_PRESENCIA = 0x01;
    private static final byte BANDERA_VERIFICACION = 0x04;
    private static final byte BANDERA_RESPALDO_POSIBLE = 0x08;
    private static final byte BANDERA_RESPALDADA = 0x10;
    private static final byte BANDERA_DATOS_DE_CREDENCIAL = 0x40;

    private final JsonMapper json = JsonMapper.builder().build();
    private final SecureRandom aleatorio = new SecureRandom();
    private final List<Clave> claves = new ArrayList<>();

    /** Una passkey que guarda el dispositivo: su id, sus claves, de quien es y su contador. */
    static final class Clave {
        final byte[] id;
        final KeyPair par;
        final String rpId;
        final byte[] handle;
        long contador;

        Clave(byte[] id, KeyPair par, String rpId, byte[] handle) {
            this.id = id;
            this.par = par;
            this.rpId = rpId;
            this.handle = handle;
        }

        String idEnBase64() {
            return b64(id);
        }
    }

    /** Lo que se puede torcer de una respuesta. */
    record Variante(String origen, boolean verificaALaPersona, Long contador, boolean firmaRota, String tipo) {

        static Variante normal() {
            return new Variante(ORIGEN, true, null, false, null);
        }

        Variante conOrigen(String otro) {
            return new Variante(otro, verificaALaPersona, contador, firmaRota, tipo);
        }

        Variante sinVerificarALaPersona() {
            return new Variante(origen, false, contador, firmaRota, tipo);
        }

        Variante conContador(long fijo) {
            return new Variante(origen, verificaALaPersona, fijo, firmaRota, tipo);
        }

        Variante conFirmaRota() {
            return new Variante(origen, verificaALaPersona, contador, true, tipo);
        }

        Variante conTipo(String otro) {
            return new Variante(origen, verificaALaPersona, contador, firmaRota, otro);
        }
    }

    List<Clave> claves() {
        return List.copyOf(claves);
    }

    /** Responde a las opciones de registro: crea una passkey nueva y devuelve su credencial. */
    ObjectNode registrar(JsonNode opciones) {
        return registrar(opciones, Variante.normal());
    }

    ObjectNode registrar(JsonNode opciones, Variante variante) {
        try {
            String rpId = opciones.path("rp").path("id").asString();
            byte[] handle = Base64.getUrlDecoder().decode(opciones.path("user").path("id").asString());
            KeyPairGenerator generador = KeyPairGenerator.getInstance("EC");
            generador.initialize(new ECGenParameterSpec("secp256r1"), aleatorio);
            byte[] id = new byte[16];
            aleatorio.nextBytes(id);
            Clave clave = new Clave(id, generador.generateKeyPair(), rpId, handle);
            claves.add(clave);

            byte[] datosDelCliente = datosDelCliente(variante.tipo() == null ? "webauthn.create" : variante.tipo(),
                    opciones.path("challenge").asString(), variante.origen());
            ByteArrayOutputStream datos = new ByteArrayOutputStream();
            datos.write(sha256(rpId.getBytes(StandardCharsets.UTF_8)));
            datos.write(banderas(variante) | BANDERA_DATOS_DE_CREDENCIAL);
            datos.write(ByteBuffer.allocate(4).putInt(0).array());
            datos.write(new byte[16]);
            datos.write(ByteBuffer.allocate(2).putShort((short) id.length).array());
            datos.write(id);
            datos.write(coseKey((ECPublicKey) clave.par.getPublic()));

            Map<Object, Object> atestacion = new LinkedHashMap<>();
            atestacion.put("fmt", "none");
            atestacion.put("attStmt", Map.of());
            atestacion.put("authData", datos.toByteArray());

            ObjectNode credencial = json.createObjectNode();
            credencial.put("id", b64(id));
            credencial.put("rawId", b64(id));
            credencial.put("type", "public-key");
            credencial.put("authenticatorAttachment", "platform");
            credencial.putObject("clientExtensionResults");
            ObjectNode respuesta = credencial.putObject("response");
            respuesta.put("clientDataJSON", b64(datosDelCliente));
            respuesta.put("attestationObject", b64(Cbor.de(atestacion)));
            respuesta.putArray("transports").add("internal");
            return credencial;
        } catch (GeneralSecurityException | java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Firma las opciones de una peticion con la passkey que ofrecen, o con la ultima creada si no ofrecen ninguna. */
    ObjectNode firmar(JsonNode opciones) {
        return firmar(opciones, Variante.normal());
    }

    ObjectNode firmar(JsonNode opciones, Variante variante) {
        Clave clave = elegir(opciones);
        return firmarCon(clave, opciones, variante);
    }

    ObjectNode firmarCon(Clave clave, JsonNode opciones, Variante variante) {
        try {
            String rpId = opciones.path("rpId").asString(clave.rpId);
            clave.contador = variante.contador() == null ? clave.contador + 1 : variante.contador();
            byte[] datosDelCliente = datosDelCliente(variante.tipo() == null ? "webauthn.get" : variante.tipo(),
                    opciones.path("challenge").asString(), variante.origen());
            ByteArrayOutputStream datos = new ByteArrayOutputStream();
            datos.write(sha256(rpId.getBytes(StandardCharsets.UTF_8)));
            datos.write(banderas(variante));
            datos.write(ByteBuffer.allocate(4).putInt((int) clave.contador).array());
            byte[] datosDelAutenticador = datos.toByteArray();

            Signature firma = Signature.getInstance("SHA256withECDSA");
            firma.initSign(clave.par.getPrivate());
            firma.update(datosDelAutenticador);
            firma.update(sha256(datosDelCliente));
            byte[] firmada = firma.sign();
            if (variante.firmaRota()) {
                firmada[firmada.length - 1] ^= 0x01;
            }

            ObjectNode credencial = json.createObjectNode();
            credencial.put("id", b64(clave.id));
            credencial.put("rawId", b64(clave.id));
            credencial.put("type", "public-key");
            credencial.put("authenticatorAttachment", "platform");
            credencial.putObject("clientExtensionResults");
            ObjectNode respuesta = credencial.putObject("response");
            respuesta.put("clientDataJSON", b64(datosDelCliente));
            respuesta.put("authenticatorData", b64(datosDelAutenticador));
            respuesta.put("signature", b64(firmada));
            respuesta.put("userHandle", b64(clave.handle));
            return credencial;
        } catch (GeneralSecurityException | java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private Clave elegir(JsonNode opciones) {
        List<String> ofrecidas = new ArrayList<>();
        opciones.path("allowCredentials").forEach(descriptor -> ofrecidas.add(descriptor.path("id").asString()));
        for (int i = claves.size() - 1; i >= 0; i--) {
            Clave clave = claves.get(i);
            if (ofrecidas.isEmpty() || ofrecidas.contains(clave.idEnBase64())) {
                return clave;
            }
        }
        throw new IllegalStateException("El autenticador no tiene ninguna de las passkeys que piden las opciones.");
    }

    private static byte banderas(Variante variante) {
        byte banderas = BANDERA_PRESENCIA | BANDERA_RESPALDO_POSIBLE | BANDERA_RESPALDADA;
        return variante.verificaALaPersona() ? (byte) (banderas | BANDERA_VERIFICACION) : banderas;
    }

    private byte[] datosDelCliente(String tipo, String desafio, String origen) {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("type", tipo);
        datos.put("challenge", desafio);
        datos.put("origin", origen);
        datos.put("crossOrigin", false);
        return json.writeValueAsBytes(datos);
    }

    /** La clave publica en COSE: EC2 (1: 2), ES256 (3: -7), P-256 (-1: 1) y sus coordenadas x (-2) e y (-3). */
    private static byte[] coseKey(ECPublicKey clave) {
        Map<Object, Object> cose = new LinkedHashMap<>();
        cose.put(1, 2);
        cose.put(3, -7);
        cose.put(-1, 1);
        cose.put(-2, coordenada(clave.getW().getAffineX()));
        cose.put(-3, coordenada(clave.getW().getAffineY()));
        return Cbor.de(cose);
    }

    private static byte[] coordenada(BigInteger valor) {
        byte[] bytes = valor.toByteArray();
        byte[] fija = new byte[32];
        int largo = Math.min(bytes.length, 32);
        System.arraycopy(bytes, bytes.length - largo, fija, 32 - largo, largo);
        return fija;
    }

    private static byte[] sha256(byte[] datos) throws GeneralSecurityException {
        return MessageDigest.getInstance("SHA-256").digest(datos);
    }

    static String b64(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Lo justo de CBOR (RFC 8949) para una atestacion y una clave COSE: enteros, bytes, textos y mapas. */
    static final class Cbor {

        private Cbor() {
        }

        static byte[] de(Object valor) {
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            escribir(salida, valor);
            return salida.toByteArray();
        }

        private static void escribir(ByteArrayOutputStream salida, Object valor) {
            switch (valor) {
                case Integer entero when entero >= 0 -> cabecera(salida, 0, entero);
                case Integer entero -> cabecera(salida, 1, -1L - entero);
                case byte[] bytes -> {
                    cabecera(salida, 2, bytes.length);
                    salida.writeBytes(bytes);
                }
                case String texto -> {
                    byte[] bytes = texto.getBytes(StandardCharsets.UTF_8);
                    cabecera(salida, 3, bytes.length);
                    salida.writeBytes(bytes);
                }
                case Map<?, ?> mapa -> {
                    cabecera(salida, 5, mapa.size());
                    mapa.forEach((clave, contenido) -> {
                        escribir(salida, clave);
                        escribir(salida, contenido);
                    });
                }
                default -> throw new IllegalArgumentException("CBOR no sabe escribir " + valor.getClass());
            }
        }

        private static void cabecera(ByteArrayOutputStream salida, int tipo, long valor) {
            int mayor = tipo << 5;
            if (valor < 24) {
                salida.write(mayor | (int) valor);
            } else if (valor < 0x100) {
                salida.write(mayor | 24);
                salida.write((int) valor);
            } else if (valor < 0x10000) {
                salida.write(mayor | 25);
                salida.write((int) (valor >> 8));
                salida.write((int) valor);
            } else {
                salida.write(mayor | 26);
                salida.writeBytes(ByteBuffer.allocate(4).putInt((int) valor).array());
            }
        }
    }
}
