package com.facimus.procesos.security;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Un proveedor de OpenID Connect de mentira para las pruebas del PR 50: publica sus claves y, en su endpoint de
 * token, cambia un codigo por un id_token RS256 firmado con lo que la prueba le pidio. Recuerda lo que le llego en
 * cada peticion de token, para comprobar PKCE y la autenticacion del cliente.
 */
final class ProveedorDePrueba implements AutoCloseable {

    static final String CLIENTE = "cliente-de-pruebas";
    static final String SECRETO = "secreto-de-pruebas";

    /** Lo que llevara el id_token del proximo codigo. */
    record Persona(String sub, String email, boolean verificado, String nonce) {
    }

    private final HttpServer servidor;
    private final RSAKey clave;
    private final Map<String, Persona> porCodigo = new ConcurrentHashMap<>();
    private volatile Map<String, String> ultimaPeticionDeToken = Map.of();
    private volatile String ultimaAutorizacion;

    ProveedorDePrueba() throws IOException, JOSEException {
        clave = new RSAKeyGenerator(2048).keyID("pruebas-1").generate();
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/jwks", intercambio -> responder(intercambio, 200,
                new JWKSet(clave.toPublicJWK()).toString()));
        servidor.createContext("/token", this::token);
        servidor.start();
    }

    String base() {
        return "http://127.0.0.1:" + servidor.getAddress().getPort();
    }

    /** El proximo intercambio de ese codigo dara un id_token de esa persona. */
    void alCambiar(String codigo, Persona persona) {
        porCodigo.put(codigo, persona);
    }

    Map<String, String> ultimaPeticionDeToken() {
        return ultimaPeticionDeToken;
    }

    String ultimaAutorizacion() {
        return ultimaAutorizacion;
    }

    private void token(HttpExchange intercambio) throws IOException {
        Map<String, String> formulario;
        try (InputStream cuerpo = intercambio.getRequestBody()) {
            formulario = Arrays.stream(new String(cuerpo.readAllBytes(), StandardCharsets.UTF_8).split("&"))
                    .map(par -> par.split("=", 2))
                    .collect(Collectors.toMap(par -> decodificar(par[0]), par -> par.length > 1 ? decodificar(par[1])
                            : ""));
        }
        ultimaPeticionDeToken = formulario;
        ultimaAutorizacion = intercambio.getRequestHeaders().getFirst("Authorization");
        Persona persona = porCodigo.remove(formulario.getOrDefault("code", ""));
        if (persona == null) {
            responder(intercambio, 400, "{\"error\":\"invalid_grant\"}");
            return;
        }
        responder(intercambio, 200, "{\"access_token\":\"acceso-de-pruebas\",\"token_type\":\"Bearer\","
                + "\"expires_in\":300,\"scope\":\"openid email profile\",\"id_token\":\"" + idToken(persona) + "\"}");
    }

    private String idToken(Persona persona) {
        Instant ahora = Instant.now();
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .issuer(base())
                .subject(persona.sub())
                .audience(CLIENTE)
                .issueTime(Date.from(ahora))
                .expirationTime(Date.from(ahora.plusSeconds(300)))
                .claim("email_verified", persona.verificado())
                .claim("name", "Persona de " + persona.sub());
        if (persona.email() != null) {
            claims.claim("email", persona.email());
        }
        if (persona.nonce() != null) {
            claims.claim("nonce", persona.nonce());
        }
        try {
            SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(clave.getKeyID()).build(),
                    claims.build());
            jwt.sign(new RSASSASigner(clave));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String decodificar(String texto) {
        return URLDecoder.decode(texto, StandardCharsets.UTF_8);
    }

    private static void responder(HttpExchange intercambio, int estado, String cuerpo) throws IOException {
        byte[] bytes = cuerpo.getBytes(StandardCharsets.UTF_8);
        intercambio.getResponseHeaders().add("Content-Type", "application/json");
        intercambio.sendResponseHeaders(estado, bytes.length);
        try (OutputStream salida = intercambio.getResponseBody()) {
            salida.write(bytes);
        }
    }

    @Override
    public void close() {
        servidor.stop(0);
    }
}
