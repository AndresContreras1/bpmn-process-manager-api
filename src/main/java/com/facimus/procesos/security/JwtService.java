package com.facimus.procesos.security;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;

import javax.crypto.SecretKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Component
public class JwtService {

    static final String CLAIM_USUARIO_ID = "usuarioId";
    static final String CLAIM_EMPRESA_ID = "empresaId";
    static final String CLAIM_ROL = "rol";

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private final SecretKey key;
    private final long expirationSeconds;

    public JwtService(@Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration-seconds}") long expirationSeconds) {
        if (secret.isBlank()) {
            log.warn("JWT_SECRET no definido: se usa una clave aleatoria y los tokens se invalidan al reiniciar.");
            this.key = Jwts.SIG.HS256.key().build();
        } else {
            this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        }
        this.expirationSeconds = expirationSeconds;
    }

    public String generarToken(ApiPrincipal principal) {
        Date ahora = new Date();
        return Jwts.builder()
                .subject(principal.email())
                .claim(CLAIM_USUARIO_ID, principal.usuarioId())
                .claim(CLAIM_EMPRESA_ID, principal.empresaId())
                .claim(CLAIM_ROL, principal.rol().name())
                .issuedAt(ahora)
                .expiration(new Date(ahora.getTime() + expirationSeconds * 1000))
                .signWith(key)
                .compact();
    }

    /** Claims del token si la firma es valida y no ha expirado; vacio en cualquier otro caso. */
    public Optional<Claims> validar(String token) {
        try {
            return Optional.of(Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload());
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public long getExpirationSeconds() {
        return expirationSeconds;
    }
}
