package com.facimus.procesos.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.facimus.procesos.ProcesosApplication;
import com.facimus.procesos.modelado.service.Dictamen;
import com.facimus.procesos.modelado.service.RevisorDeDiagramas;
import com.facimus.procesos.postgres.PostgresDePrueba;
import com.facimus.procesos.postgres.PostgresDePrueba.ConexionDePrueba;
import com.facimus.procesos.security.CookiesDeSesion;

import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import tools.jackson.databind.JsonNode;

/**
 * D34: dos instancias de la API sobre la misma base comparten lo que antes vivia en la memoria de cada una. Arrancan
 * a mano, cada una con su servidor y con la escucha de PostgreSQL encendida, sobre una base propia de esta prueba; lo
 * que pasa en una se mira en la otra.
 */
class DosInstanciasTest {

    private static final String CLAVE = "clave-de-dos-instancias";
    /** El token CSRF de este navegador de prueba: viaja en su cookie y en su cabecera, que tienen que coincidir. */
    private static final String CSRF = UUID.randomUUID().toString();
    private static final RestClient HTTP = RestClient.builder()
            .requestFactory(new SimpleClientHttpRequestFactory())
            .build();

    /** Las dos instancias tienen un modelo de prueba que siempre contesta lo mismo. */
    @TestConfiguration
    static class ModeloDePrueba {

        @Bean
        @Primary
        RevisorDeDiagramas revisorDePrueba() {
            return new RevisorDeDiagramas() {
                @Override
                public boolean estaConfigurado() {
                    return true;
                }

                @Override
                public Dictamen revisar(String diagrama) {
                    return new Dictamen("Falta el caso de error.", List.of());
                }
            };
        }
    }

    private static ConfigurableApplicationContext primera;
    private static ConfigurableApplicationContext segunda;

    @BeforeAll
    static void arrancarDosInstancias() {
        ConexionDePrueba base = PostgresDePrueba.nuevaBaseCompartida();
        primera = arrancar(base);
        segunda = arrancar(base);
    }

    @AfterAll
    static void detenerlas() {
        segunda.close();
        primera.close();
    }

    @Test
    @DisplayName("Una sesion cerrada en una instancia deja de servir en la otra, sin esperar a que venza su token")
    void sesionCerradaEnUna_dejaDeServirEnLaOtra() {
        String token = registrarTiendaYEntrar(primera, "cierre@dos-instancias.com", "900200001-1");
        assertThat(estadoDeUnaLectura(segunda, token)).isEqualTo(200);

        HTTP.post().uri(url(primera, "/api/v1/auth/logout"))
                .headers(conSesion(token))
                .contentType(MediaType.APPLICATION_JSON)
                .retrieve()
                .toBodilessEntity();

        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(estadoDeUnaLectura(segunda, token)).isEqualTo(401));
    }

    @Test
    @DisplayName("Los intentos fallidos de login cuentan en las dos: agotarlos en una bloquea el correo en la otra")
    void intentosAgotadosEnUna_bloqueanEnLaOtra() {
        registrarTiendaYEntrar(primera, "intentos@dos-instancias.com", "900200003-3");
        for (int intento = 0; intento < 5; intento++) {
            assertThat(estadoDelLogin(primera, "intentos@dos-instancias.com", "clave-equivocada")).isEqualTo(401);
        }

        assertThat(estadoDelLogin(segunda, "intentos@dos-instancias.com", CLAVE)).isEqualTo(429);
    }

    @Test
    @DisplayName("El candado de un trabajo que tomo una instancia no lo toma la otra hasta que se suelta")
    void candadoDeUna_noLoTomaLaOtra() {
        LockConfiguration trabajo = new LockConfiguration(Instant.now(), "trabajo-de-prueba", Duration.ofMinutes(1),
                Duration.ZERO);

        Optional<SimpleLock> deLaPrimera = primera.getBean(LockProvider.class).lock(trabajo);
        assertThat(deLaPrimera).isPresent();
        assertThat(segunda.getBean(LockProvider.class).lock(trabajo)).isEmpty();

        deLaPrimera.orElseThrow().unlock();
        Optional<SimpleLock> deLaSegunda = segunda.getBean(LockProvider.class).lock(trabajo);
        assertThat(deLaSegunda).isPresent();
        deLaSegunda.orElseThrow().unlock();
    }

    @Test
    @DisplayName("La revision con IA que pidio una instancia la devuelve la otra, sin volver a llamar al modelo")
    void revisionDeUna_laDevuelveLaOtra() {
        String token = registrarTiendaYEntrar(primera, "revision@dos-instancias.com", "900200002-2");
        long procesoId = crearProceso(primera, token);

        JsonNode enLaPrimera = revisar(primera, token, procesoId);
        JsonNode enLaSegunda = revisar(segunda, token, procesoId);

        assertThat(enLaPrimera.get("reutilizada").asBoolean()).isFalse();
        assertThat(enLaSegunda.get("reutilizada").asBoolean()).isTrue();
        assertThat(enLaSegunda.get("fecha").asString()).isEqualTo(enLaPrimera.get("fecha").asString());
    }

    private static ConfigurableApplicationContext arrancar(ConexionDePrueba base) {
        return new SpringApplicationBuilder(ProcesosApplication.class, ModeloDePrueba.class)
                .profiles("test")
                .run("--server.port=0", "--spring.datasource.url=" + base.url(),
                        "--spring.datasource.username=" + base.usuario(),
                        "--spring.datasource.password=" + base.clave(),
                        "--sesiones.escuchar-cierres=true");
    }

    private static String registrarTiendaYEntrar(ConfigurableApplicationContext instancia, String email, String nit) {
        HTTP.post().uri(url(instancia, "/api/v1/empresas"))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("nombreEmpresa", "Tienda " + nit, "nit", nit, "correoContacto", email,
                        "nombreAdmin", "Administradora", "emailAdmin", email, "passwordAdmin", CLAVE))
                .retrieve()
                .toBodilessEntity();
        List<String> cookies = HTTP.post().uri(url(instancia, "/api/v1/auth/login"))
                .headers(conCsrf())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", email, "password", CLAVE))
                .retrieve()
                .toBodilessEntity()
                .getHeaders().get(HttpHeaders.SET_COOKIE);
        String prefijo = CookiesDeSesion.ACCESO + "=";
        return cookies.stream()
                .filter(cookie -> cookie.startsWith(prefijo))
                .map(cookie -> cookie.substring(prefijo.length(), cookie.indexOf(';')))
                .findFirst()
                .orElseThrow();
    }

    private static long crearProceso(ConfigurableApplicationContext instancia, String token) {
        return HTTP.post().uri(url(instancia, "/api/v1/procesos"))
                .headers(conSesion(token))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("nombre", "Order fulfillment", "descripcion", "Lo que el modelo revisa",
                        "categoria", "Ventas"))
                .retrieve()
                .body(JsonNode.class)
                .get("id").asLong();
    }

    private static JsonNode revisar(ConfigurableApplicationContext instancia, String token, long procesoId) {
        return HTTP.post().uri(url(instancia, "/api/v1/procesos/" + procesoId + "/revision"))
                .headers(conSesion(token))
                .contentType(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(JsonNode.class);
    }

    private static int estadoDelLogin(ConfigurableApplicationContext instancia, String email, String clave) {
        return HTTP.post().uri(url(instancia, "/api/v1/auth/login"))
                .headers(conCsrf())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", email, "password", clave))
                .exchange((peticion, respuesta) -> respuesta.getStatusCode().value());
    }

    private static int estadoDeUnaLectura(ConfigurableApplicationContext instancia, String token) {
        return HTTP.get().uri(url(instancia, "/api/v1/procesos"))
                .headers(conSesion(token))
                .exchange((peticion, respuesta) -> respuesta.getStatusCode().value());
    }

    /** Lo que manda un navegador con su sesion: la cookie de acceso, y el token CSRF en su cookie y en su cabecera. */
    private static Consumer<HttpHeaders> conSesion(String token) {
        return cabeceras -> {
            cabeceras.add(HttpHeaders.COOKIE, CookiesDeSesion.ACCESO + "=" + token + "; XSRF-TOKEN=" + CSRF);
            cabeceras.add("X-XSRF-TOKEN", CSRF);
        };
    }

    /** Lo que manda el navegador antes de entrar: solo el token CSRF, que el login pide. */
    private static Consumer<HttpHeaders> conCsrf() {
        return cabeceras -> {
            cabeceras.add(HttpHeaders.COOKIE, "XSRF-TOKEN=" + CSRF);
            cabeceras.add("X-XSRF-TOKEN", CSRF);
        };
    }

    private static String url(ConfigurableApplicationContext instancia, String ruta) {
        return "http://localhost:" + instancia.getEnvironment().getProperty("local.server.port") + ruta;
    }
}
