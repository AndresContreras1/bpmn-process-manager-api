package com.facimus.procesos.security;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Date;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.facimus.procesos.common.model.RolAcceso;
import com.facimus.procesos.common.security.ApiPrincipal;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.LocatorAdapter;
import io.jsonwebtoken.ProtectedHeader;
import io.jsonwebtoken.security.Keys;

/**
 * El access token, con lo que pide el RFC 8725: quien lo emite ({@code iss}), para quien es ({@code aud}), un id
 * propio ({@code jti}) y en la cabecera la clave que lo firmo ({@code kid}). HS256, porque quien lo firma y quien lo
 * lee son la misma API.
 *
 * <p>Se firma siempre con {@code JWT_SECRET}, y se acepta tambien lo firmado con {@code JWT_PREVIOUS_SECRET}: rotar la
 * clave es pasar la vieja a esa variable y poner una nueva en la otra, y nadie pierde su sesion. Cuando los tokens de
 * la vieja vencieron, quince minutos despues, la anterior se quita.
 */
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

    private final SecretKey clave;
    private final String idDeLaClave;
    /** Las claves que se aceptan, por su kid: la de firmar y, mientras dure una rotacion, la anterior. */
    private final Map<String, SecretKey> aceptadas = new LinkedHashMap<>();
    private final String emisor;
    private final String audiencia;
    private final long expirationSeconds;

    public JwtService(@Value("${jwt.secret}") String secret, @Value("${jwt.previous-secret:}") String anterior,
            @Value("${jwt.issuer}") String emisor, @Value("${jwt.audience}") String audiencia,
            @Value("${jwt.expiration-seconds}") long expirationSeconds) {
        if (secret.isBlank()) {
            log.warn("JWT_SECRET no definido: se usa una clave aleatoria y los tokens se invalidan al reiniciar.");
            this.clave = Jwts.SIG.HS256.key().build();
        } else {
            this.clave = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        }
        this.idDeLaClave = idDe(clave);
        aceptadas.put(idDeLaClave, clave);
        if (!anterior.isBlank()) {
            SecretKey claveAnterior = Keys.hmacShaKeyFor(anterior.getBytes(StandardCharsets.UTF_8));
            aceptadas.putIfAbsent(idDe(claveAnterior), claveAnterior);
        }
        this.emisor = emisor;
        this.audiencia = audiencia;
        this.expirationSeconds = expirationSeconds;
    }

    public String generarToken(ApiPrincipal principal) {
        return generarToken(principal, Duration.ofSeconds(expirationSeconds));
    }

    /** Un access token que vence cuando se le pide: el de una sesion a punto de acabar, cuando ella acaba. */
    public String generarToken(ApiPrincipal principal, Duration vida) {
        Date ahora = new Date();
        return Jwts.builder()
                .header().keyId(idDeLaClave).and()
                .issuer(emisor)
                .audience().add(audiencia).and()
                .id(UUID.randomUUID().toString())
                .subject(principal.email())
                .claim(CLAIM_USUARIO_ID, principal.usuarioId())
                .claim(CLAIM_EMPRESA_ID, principal.empresaId())
                .claim(CLAIM_ROL, principal.rol().name())
                .claim(CLAIM_SESION, principal.sesion())
                .claim(CLAIM_CAMBIO_DE_CLAVE, principal.debeCambiarClave())
                .issuedAt(ahora)
                .expiration(new Date(ahora.getTime() + vida.toMillis()))
                .signWith(clave, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * La identidad del token si lo firmo una clave aceptada, lo emitio esta API para esta API, no expiro y trae todos
     * sus claims; vacio en cualquier otro caso. Sale entera de los claims, sin consultar la base.
     */
    public Optional<ApiPrincipal> validar(String token) {
        try {
            return principal(Jwts.parser()
                    .keyLocator(new LocatorAdapter<Key>() {
                        @Override
                        protected Key locate(ProtectedHeader header) {
                            String kid = header.getKeyId();
                            return kid == null ? null : aceptadas.get(kid);
                        }
                    })
                    .requireIssuer(emisor)
                    .requireAudience(audiencia)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload());
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public long getExpirationSeconds() {
        return expirationSeconds;
    }

    /**
     * El kid de una clave: los primeros 16 caracteres hexadecimales de su SHA-256. Distingue las claves sin decir nada
     * de ellas, y no cambia mientras la clave no cambie.
     */
    static String idDe(SecretKey clave) {
        try {
            byte[] huella = MessageDigest.getInstance("SHA-256").digest(clave.getEncoded());
            return HexFormat.of().formatHex(huella, 0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 tiene que existir en toda JVM.", e);
        }
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
