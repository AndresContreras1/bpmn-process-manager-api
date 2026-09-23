package com.facimus.procesos.modelado.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.facimus.procesos.common.IntegracionFallidaException;
import com.facimus.procesos.modelado.model.Severidad;
import com.facimus.procesos.modelado.service.impl.GeminiRevisor;

import tools.jackson.databind.json.JsonMapper;

/**
 * El cliente del revisor contra un servidor simulado: que pida lo que tiene que pedir, que lea la respuesta que el
 * modelo devuelve, y que falle de forma clara cuando lo que llega no sirve. No sale una sola peticion de red.
 */
class GeminiRevisorTest {

    private static final String URL = "https://gemini.test/v1beta";
    private static final String DIAGRAMA = "Proceso: Order fulfillment (PUBLICADO)";

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private MockRestServiceServer servidor;
    private GeminiRevisor revisor;

    @BeforeEach
    void prepararElServidorSimulado() {
        RestClient.Builder builder = RestClient.builder().baseUrl(URL);
        servidor = MockRestServiceServer.bindTo(builder).build();
        revisor = new GeminiRevisor(builder.build(), jsonMapper, "clave-de-prueba", "gemini-3.8-flash");
    }

    @Test
    @DisplayName("Manda el diagrama con el formato exigido y devuelve los hallazgos que contesta el modelo")
    void revisar_respuestaDelModelo_devuelveElDictamen() {
        servidor.expect(requestTo(URL + "/interactions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", "clave-de-prueba"))
                .andExpect(jsonPath("$.model").value("gemini-3.8-flash"))
                .andExpect(jsonPath("$.response_format.mime_type").value("application/json"))
                .andExpect(jsonPath("$.response_format.schema.required[0]").exists())
                .andExpect(content().string(containsString(DIAGRAMA)))
                .andRespond(withSuccess(respuesta("""
                        {"resumen":"El proceso no dice que pasa si el pago falla.",
                         "hallazgos":[{"severidad":"ALTA","elemento":"Gateway: Payment approved?",
                         "problema":"Solo tiene una salida.","sugerencia":"Agrega la rama del pago rechazado."}]}"""),
                        MediaType.APPLICATION_JSON));

        Dictamen dictamen = revisor.revisar(DIAGRAMA);

        assertThat(dictamen.resumen()).contains("pago falla");
        assertThat(dictamen.hallazgos()).singleElement().satisfies(hallazgo -> {
            assertThat(hallazgo.severidad()).isEqualTo(Severidad.ALTA);
            assertThat(hallazgo.elemento()).isEqualTo("Gateway: Payment approved?");
            assertThat(hallazgo.sugerencia()).contains("rechazado");
        });
        servidor.verify();
    }

    @Test
    @DisplayName("Un diagrama sin problemas devuelve la lista de hallazgos vacia, no un error")
    void revisar_sinHallazgos_devuelveListaVacia() {
        servidor.expect(requestTo(URL + "/interactions"))
                .andRespond(withSuccess(respuesta("{\"resumen\":\"El proceso esta completo.\",\"hallazgos\":[]}"),
                        MediaType.APPLICATION_JSON));

        assertThat(revisor.revisar(DIAGRAMA).hallazgos()).isEmpty();
    }

    @Test
    @DisplayName("Si el modelo contesta algo que no encaja en el formato, la peticion falla en vez de inventar")
    void revisar_fueraDeFormato_lanzaIntegracionFallida() {
        servidor.expect(requestTo(URL + "/interactions"))
                .andRespond(withSuccess(respuesta("lo siento, no puedo"), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> revisor.revisar(DIAGRAMA))
                .isInstanceOf(IntegracionFallidaException.class)
                .hasMessageContaining("formato");
    }

    @Test
    @DisplayName("Si la respuesta no trae la salida del modelo, tampoco se inventa un dictamen")
    void revisar_sinSalidaDelModelo_lanzaIntegracionFallida() {
        servidor.expect(requestTo(URL + "/interactions"))
                .andRespond(withSuccess("{\"status\":\"completed\",\"steps\":[]}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> revisor.revisar(DIAGRAMA))
                .isInstanceOf(IntegracionFallidaException.class)
                .hasMessageContaining("no devolvió");
    }

    @Test
    @DisplayName("Un error del servicio se convierte en un fallo de integracion, no en un 500 sin explicacion")
    void revisar_errorDelServicio_lanzaIntegracionFallida() {
        servidor.expect(requestTo(URL + "/interactions")).andRespond(withServerError());

        assertThatThrownBy(() -> revisor.revisar(DIAGRAMA))
                .isInstanceOf(IntegracionFallidaException.class)
                .hasMessageContaining("no respondió");
    }

    @Test
    @DisplayName("Sin clave la funcion queda apagada, y nadie gasta una llamada")
    void estaConfigurado_sinClave_esFalso() {
        RestClient.Builder builder = RestClient.builder().baseUrl(URL);
        MockRestServiceServer.bindTo(builder).build();
        GeminiRevisor sinClave = new GeminiRevisor(builder.build(), jsonMapper, "  ", "gemini-3.8-flash");

        assertThat(sinClave.estaConfigurado()).isFalse();
        assertThat(revisor.estaConfigurado()).isTrue();
    }

    /** La forma en la que contesta la API: los pasos del modelo, y en uno de ellos el texto con el JSON pedido. */
    private String respuesta(String textoDelModelo) {
        return jsonMapper.writeValueAsString(Map.of(
                "id", "v1_prueba",
                "status", "completed",
                "object", "interaction",
                "model", "gemini-3.8-flash",
                "steps", List.of(Map.of(
                        "type", "model_output",
                        "content", List.of(Map.of("type", "text", "text", textoDelModelo))))));
    }
}
