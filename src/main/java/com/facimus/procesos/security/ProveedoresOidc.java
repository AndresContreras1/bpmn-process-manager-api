package com.facimus.procesos.security;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.util.StringUtils;

/**
 * PR 50: los proveedores de OpenID Connect, desde {@code oidc.proveedores.<id>.*}. Google y Microsoft traen sus
 * direcciones; basta con su client-id y su client-secret. Uno sin client-id no existe, y sin ninguno la API no
 * ofrece entrar con un proveedor: Spring Boot no puede crear los registros por su cuenta, porque un client-id vacio
 * no lo deja arrancar.
 */
@Configuration
public class ProveedoresOidc {

    /** Donde vuelve el navegador desde el proveedor. {baseUrl} sale de las cabeceras del proxy (PR 33). */
    static final String REDIRECCION = "{baseUrl}/api/v1/auth/oidc/callback/{registrationId}";

    static final String GOOGLE = "google";
    static final String MICROSOFT = "microsoft";

    /** Un proveedor, como se configura. Lo que se deja vacio en Google y Microsoft lo pone esta clase. */
    public record Proveedor(String clientId, String clientSecret, String tenant, String authorizationUri,
            String tokenUri, String jwkSetUri, String issuer) {
    }

    /** Los registros que hay, si hay alguno, y sus ids en orden, que es lo que la web ofrece como botones. */
    public record Clientes(Optional<ClientRegistrationRepository> repositorio, List<String> proveedores) {

        public boolean configurados() {
            return repositorio.isPresent();
        }
    }

    @Bean
    public Clientes clientesOidc(Environment entorno) {
        Map<String, Proveedor> configurados = Binder.get(entorno)
                .bind("oidc.proveedores", Bindable.mapOf(String.class, Proveedor.class))
                .orElse(Map.of());
        Map<String, ClientRegistration> registros = new LinkedHashMap<>();
        configurados.forEach((id, proveedor) -> {
            if (StringUtils.hasText(proveedor.clientId())) {
                registros.put(id, registro(id, proveedor));
            }
        });
        if (registros.isEmpty()) {
            return new Clientes(Optional.empty(), List.of());
        }
        return new Clientes(Optional.of(new InMemoryClientRegistrationRepository(List.copyOf(registros.values()))),
                List.copyOf(registros.keySet()));
    }

    static ClientRegistration registro(String id, Proveedor proveedor) {
        ClientRegistration.Builder registro = ClientRegistration.withRegistrationId(id)
                .clientId(proveedor.clientId())
                .clientSecret(proveedor.clientSecret())
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri(REDIRECCION)
                .scope("openid", "email", "profile")
                .userNameAttributeName(IdTokenClaimNames.SUB);
        switch (id) {
            case GOOGLE -> registro.clientName("Google")
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                    .authorizationUri(o(proveedor.authorizationUri(), "https://accounts.google.com/o/oauth2/v2/auth"))
                    .tokenUri(o(proveedor.tokenUri(), "https://oauth2.googleapis.com/token"))
                    .jwkSetUri(o(proveedor.jwkSetUri(), "https://www.googleapis.com/oauth2/v3/certs"))
                    .issuerUri(o(proveedor.issuer(), "https://accounts.google.com"));
            case MICROSOFT -> {
                // Con un tenant comun ("organizations" o "common") el emisor es el de cada organizacion: no hay uno
                // fijo que comparar, y lo comprueba DecodificadorDeIdToken contra el tid del propio token.
                String base = "https://login.microsoftonline.com/" + o(proveedor.tenant(), "organizations");
                registro.clientName("Microsoft")
                        .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                        .authorizationUri(o(proveedor.authorizationUri(), base + "/oauth2/v2.0/authorize"))
                        .tokenUri(o(proveedor.tokenUri(), base + "/oauth2/v2.0/token"))
                        .jwkSetUri(o(proveedor.jwkSetUri(), base + "/discovery/v2.0/keys"));
            }
            default -> registro.clientName(id)
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                    .authorizationUri(proveedor.authorizationUri())
                    .tokenUri(proveedor.tokenUri())
                    .jwkSetUri(proveedor.jwkSetUri())
                    .issuerUri(proveedor.issuer());
        }
        return registro.build();
    }

    private static String o(String dado, String porDefecto) {
        return StringUtils.hasText(dado) ? dado : porDefecto;
    }
}
