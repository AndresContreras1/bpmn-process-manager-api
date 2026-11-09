package com.facimus.procesos.security;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.client.oidc.authentication.OidcIdTokenDecoderFactory;
import org.springframework.security.oauth2.client.oidc.authentication.OidcIdTokenValidator;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.core.converter.ClaimTypeConverter;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoderFactory;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * PR 50: el que comprueba el id_token de cada proveedor: la firma RS256 con las claves publicas que el proveedor
 * publica, las fechas, la audiencia y el emisor. El de Spring compara el emisor con uno fijo, y con Microsoft para
 * cualquier organizacion no lo hay: ahi el emisor tiene que ser el de la organizacion que el propio token nombra en
 * {@code tid}. Las claves se piden con el cliente HTTP de siempre del proyecto.
 */
@Component
public class DecodificadorDeIdToken implements JwtDecoderFactory<ClientRegistration> {

    private static final Pattern EMISOR_DE_MICROSOFT =
            Pattern.compile("^https://login\\.microsoftonline\\.com/([0-9a-f-]{36})/v2\\.0$");

    private final Map<String, JwtDecoder> decodificadores = new ConcurrentHashMap<>();

    @Override
    public JwtDecoder createDecoder(ClientRegistration registro) {
        return decodificadores.computeIfAbsent(registro.getRegistrationId(), id -> crear(registro));
    }

    private static JwtDecoder crear(ClientRegistration registro) {
        NimbusJwtDecoder decodificador = NimbusJwtDecoder
                .withJwkSetUri(registro.getProviderDetails().getJwkSetUri())
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .restOperations(new RestTemplate(new SimpleClientHttpRequestFactory()))
                .build();
        // Los mismos tipos que usa el de Spring: email_verified como booleano, iss como URL, las fechas como Instant.
        decodificador.setClaimSetConverter(new ClaimTypeConverter(
                OidcIdTokenDecoderFactory.createDefaultClaimTypeConverters()));
        // El de los demas lo compara OidcIdTokenValidator con el emisor del registro.
        OAuth2TokenValidator<Jwt> emisor = ProveedoresOidc.MICROSOFT.equals(registro.getRegistrationId())
                ? DecodificadorDeIdToken::emisorDeMicrosoft : jwt -> OAuth2TokenValidatorResult.success();
        decodificador.setJwtValidator(new DelegatingOAuth2TokenValidator<>(new JwtTimestampValidator(),
                new OidcIdTokenValidator(registro), emisor));
        return decodificador;
    }

    /** El emisor de Microsoft es el de la organizacion del token: tiene su tid dentro, y tienen que coincidir. */
    static OAuth2TokenValidatorResult emisorDeMicrosoft(Jwt jwt) {
        String emisor = jwt.getClaimAsString("iss");
        String organizacion = jwt.getClaimAsString("tid");
        var coincide = emisor == null ? null : EMISOR_DE_MICROSOFT.matcher(emisor);
        if (coincide != null && coincide.matches() && coincide.group(1).equals(organizacion)) {
            return OAuth2TokenValidatorResult.success();
        }
        return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_id_token",
                "El emisor del id_token no es el de la organizacion que nombra.", null));
    }
}
