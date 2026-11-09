package com.facimus.procesos.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.jwt.Jwt;

/** PR 50: los registros de Google y Microsoft, y el emisor de Microsoft para cualquier organizacion. */
class ProveedoresOidcTest {

    private static final String ORGANIZACION = "72f988bf-86f1-41af-91ab-2d7cd011db47";

    @Test
    @DisplayName("Google y Microsoft traen sus direcciones; basta con el client-id y el client-secret")
    void direccionesPorDefecto() {
        ClientRegistration google = ProveedoresOidc.registro("google",
                new ProveedoresOidc.Proveedor("id-g", "secreto-g", null, null, null, null, null));
        ClientRegistration microsoft = ProveedoresOidc.registro("microsoft",
                new ProveedoresOidc.Proveedor("id-m", "secreto-m", null, null, null, null, null));

        assertThat(google.getProviderDetails().getAuthorizationUri())
                .isEqualTo("https://accounts.google.com/o/oauth2/v2/auth");
        assertThat(google.getProviderDetails().getIssuerUri()).isEqualTo("https://accounts.google.com");
        assertThat(google.getScopes()).containsExactlyInAnyOrder("openid", "email", "profile");
        assertThat(google.getRedirectUri()).isEqualTo("{baseUrl}/api/v1/auth/oidc/callback/{registrationId}");
        assertThat(microsoft.getProviderDetails().getTokenUri())
                .isEqualTo("https://login.microsoftonline.com/organizations/oauth2/v2.0/token");
        assertThat(microsoft.getProviderDetails().getIssuerUri()).isNull();
        assertThat(microsoft.getClientAuthenticationMethod()).isEqualTo(ClientAuthenticationMethod.CLIENT_SECRET_POST);
    }

    @Test
    @DisplayName("Un proveedor sin client-id no se ofrece, y sin ninguno no hay entrada con proveedores")
    void sinClientId() {
        StandardEnvironment entorno = new StandardEnvironment();
        entorno.getPropertySources().addFirst(new MapPropertySource("pruebas", Map.of(
                "oidc.proveedores.google.client-id", "", "oidc.proveedores.microsoft.client-id", "id-m",
                "oidc.proveedores.microsoft.client-secret", "s")));
        ProveedoresOidc.Clientes clientes = new ProveedoresOidc().clientesOidc(entorno);
        assertThat(clientes.proveedores()).containsExactly("microsoft");

        StandardEnvironment vacio = new StandardEnvironment();
        assertThat(new ProveedoresOidc().clientesOidc(vacio).configurados()).isFalse();
    }

    @Test
    @DisplayName("El emisor de Microsoft es el de la organizacion que el token nombra en tid, y ninguno mas")
    void emisorDeMicrosoft() {
        assertThat(DecodificadorDeIdToken.emisorDeMicrosoft(token(
                "https://login.microsoftonline.com/" + ORGANIZACION + "/v2.0", ORGANIZACION)).hasErrors()).isFalse();
        assertThat(DecodificadorDeIdToken.emisorDeMicrosoft(token(
                "https://login.microsoftonline.com/" + ORGANIZACION + "/v2.0", "00000000-0000-0000-0000-000000000000"))
                .hasErrors()).isTrue();
        assertThat(DecodificadorDeIdToken.emisorDeMicrosoft(token(
                "https://evil.example/" + ORGANIZACION + "/v2.0", ORGANIZACION)).hasErrors()).isTrue();
        assertThat(DecodificadorDeIdToken.emisorDeMicrosoft(token(null, ORGANIZACION)).hasErrors()).isTrue();
    }

    private static Jwt token(String emisor, String organizacion) {
        Jwt.Builder token = Jwt.withTokenValue("t").header("alg", "RS256").subject("s")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).claim("tid", organizacion);
        if (emisor != null) {
            token.claim("iss", emisor);
        }
        return token.build();
    }
}
