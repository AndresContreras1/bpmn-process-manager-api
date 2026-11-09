package com.facimus.procesos.security;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.stereotype.Component;

import com.facimus.procesos.common.Huella;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

/**
 * PR 50: la peticion de autorizacion que va al proveedor (su state, su nonce y el verificador de PKCE) vive en
 * PostgreSQL, porque la API no tiene sesion HTTP y cualquier instancia puede recibir la vuelta. Queda atada al
 * navegador que la empezo con una cookie {@code __Host-oidc} de 256 bits, de la que solo se guarda el SHA-256: quien
 * le pase a otro la direccion de vuelta de su propio login no lo mete en su cuenta. Sirve una vez y vence a los diez
 * minutos.
 */
@Component
public class PeticionesOidc implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    static final String COOKIE = "__Host-oidc";

    /** Lo que se espera a que la persona vuelva del proveedor. */
    static final Duration VIGENCIA = Duration.ofMinutes(10);

    private static final int BYTES_DEL_NAVEGADOR = 32;

    /** Lo que hace falta para rehacer la peticion: todo son textos. */
    record Guardada(String authorizationUri, String clientId, String redirectUri, Set<String> scopes, String state,
            Map<String, Object> additionalParameters, Map<String, Object> attributes,
            String authorizationRequestUri) {
    }

    private final JdbcTemplate jdbc;
    private final JsonMapper json;
    private final SecureRandom aleatorio = new SecureRandom();

    public PeticionesOidc(JdbcTemplate jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest peticion) {
        return buscar(peticion).orElse(null);
    }

    @Override
    public void saveAuthorizationRequest(OAuth2AuthorizationRequest autorizacion, HttpServletRequest peticion,
            HttpServletResponse respuesta) {
        if (autorizacion == null) {
            removeAuthorizationRequest(peticion, respuesta);
            return;
        }
        LocalDateTime ahora = ahora();
        jdbc.update("delete from peticiones_oidc where creada_en < ?", ahora.minus(VIGENCIA));
        byte[] bytes = new byte[BYTES_DEL_NAVEGADOR];
        aleatorio.nextBytes(bytes);
        String navegador = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Guardada guardada = new Guardada(autorizacion.getAuthorizationUri(), autorizacion.getClientId(),
                autorizacion.getRedirectUri(), autorizacion.getScopes(), autorizacion.getState(),
                autorizacion.getAdditionalParameters(), autorizacion.getAttributes(),
                autorizacion.getAuthorizationRequestUri());
        jdbc.update("insert into peticiones_oidc (estado_hash, navegador_hash, datos, creada_en) values (?, ?, ?, ?)",
                Huella.de(autorizacion.getState()), Huella.de(navegador), json.writeValueAsString(guardada), ahora);
        respuesta.addHeader(HttpHeaders.SET_COOKIE, cookie(navegador, VIGENCIA));
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest peticion,
            HttpServletResponse respuesta) {
        Optional<OAuth2AuthorizationRequest> encontrada = buscar(peticion);
        encontrada.ifPresent(autorizacion -> jdbc.update("delete from peticiones_oidc where estado_hash = ?",
                Huella.de(autorizacion.getState())));
        respuesta.addHeader(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO));
        return encontrada.orElse(null);
    }

    /** La del state de la vuelta, si la empezo este navegador y no vencio. */
    private Optional<OAuth2AuthorizationRequest> buscar(HttpServletRequest peticion) {
        String estado = peticion.getParameter(OAuth2ParameterNames.STATE);
        Optional<String> navegador = CookiesDeSesion.leer(peticion, COOKIE);
        if (estado == null || navegador.isEmpty()) {
            return Optional.empty();
        }
        List<String> datos = jdbc.queryForList("select datos from peticiones_oidc where estado_hash = ? "
                + "and navegador_hash = ? and creada_en >= ?", String.class, Huella.de(estado),
                Huella.de(navegador.get()), ahora().minus(VIGENCIA));
        return datos.stream().findFirst().map(this::rehacer);
    }

    private OAuth2AuthorizationRequest rehacer(String datos) {
        Guardada guardada = json.readValue(datos, Guardada.class);
        return OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri(guardada.authorizationUri())
                .clientId(guardada.clientId())
                .redirectUri(guardada.redirectUri())
                .scopes(guardada.scopes())
                .state(guardada.state())
                .additionalParameters(guardada.additionalParameters())
                .attributes(guardada.attributes())
                .authorizationRequestUri(guardada.authorizationRequestUri())
                .build();
    }

    /** Como guarda Hibernate las fechas: en UTC. */
    private static LocalDateTime ahora() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    /** {@code __Host-}: Secure, en / y sin dominio. Lax, porque la vuelta del proveedor es una navegacion. */
    private static String cookie(String valor, Duration vida) {
        return ResponseCookie.from(COOKIE, valor).httpOnly(true).secure(true).sameSite("Lax").path("/").maxAge(vida)
                .build().toString();
    }
}
