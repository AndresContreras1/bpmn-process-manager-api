package com.facimus.procesos.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;
import java.util.Map;

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

import tools.jackson.databind.JsonNode;

/**
 * D34: dos instancias de la API sobre la misma base comparten lo que antes vivia en la memoria de cada una. Arrancan
 * a mano, cada una con su servidor y con la escucha de PostgreSQL encendida, sobre una base propia de esta prueba; lo
 * que pasa en una se mira en la otra.
 */
class DosInstanciasTest {

    private static final String CLAVE = "clave-de-dos-instancias";
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
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .retrieve()
                .toBodilessEntity();

        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(estadoDeUnaLectura(segunda, token)).isEqualTo(401));
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
        JsonNode sesion = HTTP.post().uri(url(instancia, "/api/v1/auth/login"))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", email, "password", CLAVE))
                .retrieve()
                .body(JsonNode.class);
        return sesion.get("accessToken").asString();
    }

    private static long crearProceso(ConfigurableApplicationContext instancia, String token) {
        return HTTP.post().uri(url(instancia, "/api/v1/procesos"))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("nombre", "Order fulfillment", "descripcion", "Lo que el modelo revisa",
                        "categoria", "Ventas"))
                .retrieve()
                .body(JsonNode.class)
                .get("id").asLong();
    }

    private static JsonNode revisar(ConfigurableApplicationContext instancia, String token, long procesoId) {
        return HTTP.post().uri(url(instancia, "/api/v1/procesos/" + procesoId + "/revision"))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(JsonNode.class);
    }

    private static int estadoDeUnaLectura(ConfigurableApplicationContext instancia, String token) {
        return HTTP.get().uri(url(instancia, "/api/v1/procesos"))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange((peticion, respuesta) -> respuesta.getStatusCode().value());
    }

    private static String url(ConfigurableApplicationContext instancia, String ruta) {
        return "http://localhost:" + instancia.getEnvironment().getProperty("local.server.port") + ruta;
    }
}
