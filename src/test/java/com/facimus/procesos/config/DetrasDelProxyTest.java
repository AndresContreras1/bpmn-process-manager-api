package com.facimus.procesos.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

/**
 * Prod detras del proxy. Lo que hay delante (NGINX, y delante de NGINX el que termina el HTTPS) dice en
 * X-Forwarded-For quien es el cliente y en X-Forwarded-Proto por donde llego; Tomcat lo cree solo si la conexion viene
 * de la red interna, como la de esta prueba. Es un servidor de verdad porque esas cabeceras las lee Tomcat, no Spring.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"JWT_SECRET=prod-profile-test-signing-key-with-32-characters", "management.server.port=0"})
@ActiveProfiles("prod")
class DetrasDelProxyTest {

    @LocalServerPort
    private int puerto;

    @Autowired
    private JdbcTemplate jdbc;

    private final RestClient cliente = RestClient.builder()
            .requestFactory(new SimpleClientHttpRequestFactory())
            .build();

    @Test
    @DisplayName("Lo que llego al proxy por HTTPS recibe HSTS, y lo que llego por HTTP no")
    void loQueLlegoPorHttps_recibeHsts() {
        assertThat(hsts("https")).isEqualTo("max-age=31536000 ; includeSubDomains");
        assertThat(hsts("http")).isNull();
    }

    @Test
    @DisplayName("El limite del login cuenta al cliente que esta detras del proxy, no al proxy")
    void limiteDelLogin_cuentaAlClienteDetrasDelProxy() {
        int estado = cliente.post()
                .uri("http://localhost:" + puerto + "/api/v1/auth/login")
                .header("X-Forwarded-For", "203.0.113.7")
                // El login pide el token CSRF, como lo manda la web: el de su cookie, devuelto en la cabecera.
                .header(HttpHeaders.COOKIE, "XSRF-TOKEN=token-del-navegador")
                .header("X-XSRF-TOKEN", "token-del-navegador")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"email\":\"nadie@proxy.com\",\"password\":\"una-clave-que-no-es\"}")
                .exchange((peticion, respuesta) -> respuesta.getStatusCode().value());

        assertThat(estado).isEqualTo(401);
        assertThat(jdbc.queryForList("select clave from intentos_login where clave like 'nadie@proxy.com|%'",
                String.class)).containsExactly("nadie@proxy.com|203.0.113.7");
    }

    private String hsts(String protocolo) {
        return cliente.get()
                .uri("http://localhost:" + puerto + "/api/v1/procesos")
                .header("X-Forwarded-Proto", protocolo)
                .exchange((peticion, respuesta) -> respuesta.getHeaders().getFirst("Strict-Transport-Security"));
    }
}
