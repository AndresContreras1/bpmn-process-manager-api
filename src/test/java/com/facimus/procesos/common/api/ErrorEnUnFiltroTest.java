package com.facimus.procesos.common.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClient;

import jakarta.servlet.Filter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Lo que falla fuera de un controller, con un servidor de verdad: el contenedor manda la peticion a /error, que
 * responde en Problem Details con el mismo id de la peticion que fallo y sin contar que paso por dentro. Con MockMvc
 * no hay despacho de error, por eso esta prueba arranca Tomcat.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ErrorEnUnFiltroTest {

    private static final String RUTA = "/prueba-de-errores/filtro-que-falla";
    private static final String DETALLE_INTERNO = "detalle interno de un filtro";

    @LocalServerPort
    private int puerto;

    @Autowired
    private JsonMapper jsonMapper;

    /** Un filtro que falla siempre en su ruta, antes que la seguridad: como falla un error de programacion. */
    @TestConfiguration
    static class FiltroQueFalla {

        @Bean
        FilterRegistrationBean<Filter> filtroQueFalla() {
            FilterRegistrationBean<Filter> registro = new FilterRegistrationBean<>((peticion, respuesta, cadena) -> {
                throw new IllegalStateException(DETALLE_INTERNO);
            });
            registro.addUrlPatterns(RUTA);
            registro.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
            return registro;
        }
    }

    @Test
    @DisplayName("Un filtro que lanza termina en un 500 en Problem Details, con el id de la peticion y sin detalle")
    void filtroQueLanza_500EnProblemDetailsConElMismoId() throws Exception {
        Respuesta respuesta = pedir("id-de-prueba-500");
        JsonNode cuerpo = jsonMapper.readTree(respuesta.cuerpo());

        assertThat(respuesta.estado()).isEqualTo(500);
        assertThat(respuesta.tipo()).startsWith("application/problem+json");
        assertThat(respuesta.id()).isEqualTo("id-de-prueba-500");
        assertThat(cuerpo.get("title").asString()).isEqualTo("Error interno");
        assertThat(cuerpo.get("requestId").asString()).isEqualTo("id-de-prueba-500");
        assertThat(respuesta.cuerpo()).doesNotContain(DETALLE_INTERNO).doesNotContain("IllegalStateException");
    }

    private Respuesta pedir(String id) {
        return RestClient.builder().requestFactory(new SimpleClientHttpRequestFactory()).build()
                .get().uri("http://localhost:" + puerto + RUTA)
                .header(IdDePeticionFilter.CABECERA, id)
                .exchange((peticion, respuesta) -> new Respuesta(respuesta.getStatusCode().value(),
                        String.valueOf(respuesta.getHeaders().getContentType()),
                        respuesta.getHeaders().getFirst(IdDePeticionFilter.CABECERA),
                        StreamUtils.copyToString(respuesta.getBody(), StandardCharsets.UTF_8)));
    }

    private record Respuesta(int estado, String tipo, String id, String cuerpo) {
    }
}
