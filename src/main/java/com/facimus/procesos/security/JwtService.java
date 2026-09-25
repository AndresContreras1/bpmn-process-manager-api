package com.facimus.procesos.security;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.facimus.procesos.common.model.RolAcceso;
import com.facimus.procesos.common.security.ApiPrincipal;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import javax.crypto.SecretKey;

@Component
public class JwtService {

    static final String CLAIM_USUARIO_ID = "usuarioId";
    static final String CLAIM_EMPRESA_ID = "empresaId";
    static final String CLAIM_ROL = "rol";
    /** Claim registrado de OpenID Connect para la sesion que emitio el token. */
    static final String CLAIM_SESION = "sid";
    /** D17: si entro con una clave temporal, el token lo dice y el filtro no tiene que consultar la base. */
    static final String CLAIM_CAMBIO_DE_CLAVE = "debeCambiarClave";

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
                .claim(CLAIM_SESION, principal.sesion())
                .claim(CLAIM_CAMBIO_DE_CLAVE, principal.debeCambiarClave())
                .issuedAt(ahora)
                .expiration(new Date(ahora.getTime() + expirationSeconds * 1000))
                .signWith(key)
                .compact();
    }

    /**
     * La identidad del token si la firma es valida, no expiro y trae todos sus claims; vacio en cualquier otro caso.
     * Sale entera de los claims, sin consultar la base.
     */
    public Optional<ApiPrincipal> validar(String token) {
        try {
            return principal(Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload());
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public long getExpirationSeconds() {
        return expirationSeconds;
    }

    /** Un rol desconocido lanza IllegalArgumentException, y validar lo trata como un token invalido. */
    private static Optional<ApiPrincipal> principal(Claims claims) {
        Long usuarioId = claims.get(CLAIM_USUARIO_ID, Long.class);
        Long empresaId = claims.get(CLAIM_EMPRESA_ID, Long.class);
        String rol = claims.get(CLAIM_ROL, String.class);
        String sesion = claims.get(CLAIM_SESION, String.class);
        if (usuarioId == null || empresaId == null || rol == null || sesion == null || claims.getSubject() == null) {
            return Optional.empty();
        }
        // Un token emitido antes de que existiera el claim no obliga a nadie a cambiar nada.
        boolean debeCambiarClave = Boolean.TRUE.equals(claims.get(CLAIM_CAMBIO_DE_CLAVE, Boolean.class));
        return Optional.of(new ApiPrincipal(usuarioId, empresaId, RolAcceso.valueOf(rol), claims.getSubject(), sesion,
                debeCambiarClave));
    }
}
