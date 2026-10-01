package com.facimus.procesos.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClient;

/**
 * Actuator en su propio puerto, como en prod: el de la API no publica nada de Actuator, y el de gestion, que solo se
 * alcanza desde la red interna, publica las sondas y lo que lee Prometheus. Arranca un servidor de verdad en puertos
 * libres, porque con MockMvc no hay puertos que separar.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "management.server.port=0")
@ActiveProfiles("test")
class PuertoDeGestionTest {

    @LocalServerPort
    private int puertoDeLaApi;

    @LocalManagementPort
    private int puertoDeGestion;

    private final RestClient cliente = RestClient.builder()
            .requestFactory(new SimpleClientHttpRequestFactory())
            .build();

    @Test
    @DisplayName("Los dos puertos son distintos")
    void puertosDistintos() {
        assertThat(puertoDeGestion).isNotEqualTo(puertoDeLaApi);
    }

    @Test
    @DisplayName("El puerto de la API no publica Actuator: ni las metricas de Prometheus ni el estado")
    void puertoDeLaApi_sinActuator() {
        assertThat(pedir(puertoDeLaApi, "/actuator/prometheus").estado()).isEqualTo(404);
        assertThat(pedir(puertoDeLaApi, "/actuator/health").estado()).isEqualTo(404);
    }

    @Test
    @DisplayName("El puerto de gestion publica para Prometheus, sin token, con la etiqueta de la aplicacion")
    void puertoDeGestion_publicaPrometheus() {
        Respuesta respuesta = pedir(puertoDeGestion, "/actuator/prometheus");

        assertThat(respuesta.estado()).isEqualTo(200);
        assertThat(respuesta.cuerpo())
                .contains("jvm_memory_used_bytes")
                .contains("application=\"procesos\"")
                .contains("casos_abiertos");
    }

    @Test
    @DisplayName("Las sondas estan en el puerto de gestion, que es el que mira el contenedor")
    void puertoDeGestion_publicaLasSondas() {
        Respuesta respuesta = pedir(puertoDeGestion, "/actuator/health/readiness");

        assertThat(respuesta.estado()).isEqualTo(200);
        assertThat(respuesta.cuerpo()).contains("\"status\":\"UP\"");
    }

    @Test
    @DisplayName("En el puerto de gestion las metricas de Actuator siguen pidiendo un administrador")
    void puertoDeGestion_metricasPidenAdministrador() {
        assertThat(pedir(puertoDeGestion, "/actuator/metrics").estado()).isEqualTo(401);
    }

    private Respuesta pedir(int puerto, String ruta) {
        return cliente.get().uri("http://localhost:" + puerto + ruta).exchange((peticion, respuesta) -> new Respuesta(
                respuesta.getStatusCode().value(),
                StreamUtils.copyToString(respuesta.getBody(), StandardCharsets.UTF_8)));
    }

    private record Respuesta(int estado, String cuerpo) {
    }
}
