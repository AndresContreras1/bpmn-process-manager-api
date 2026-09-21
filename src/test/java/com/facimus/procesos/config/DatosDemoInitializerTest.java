package com.facimus.procesos.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.facimus.procesos.gestion.controller.dto.LoginRequest;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Los datos demo vistos a traves de la API, como los ve quien entra a Swagger con la cuenta de demostracion. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:datos-demo;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class DatosDemoInitializerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    private String token;

    @BeforeEach
    void iniciarSesionComoAdminDemo() throws Exception {
        String respuesta = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new LoginRequest("admin@demo.com", "admin123"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        token = jsonMapper.readTree(respuesta).get("accessToken").asString();
    }

    @Test
    @DisplayName("La tienda demo arranca con el proceso de pedidos publicado y el de devoluciones en borrador")
    void DatosDemo_primerArranque_siembraLosProcesosDeLaTienda() throws Exception {
        mockMvc.perform(conToken(get("/api/v1/procesos")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[?(@.nombre == 'Order fulfillment')].estado").value("PUBLICADO"))
                .andExpect(jsonPath("$.content[?(@.nombre == 'Returns and refunds')].estado").value("BORRADOR"));
    }

    @Test
    @DisplayName("El proceso de pedidos tiene cuatro participantes y el flujo de la tienda en dos lanes")
    void DatosDemo_procesoDePedidos_tieneParticipantesYFlujoDeLaTienda() throws Exception {
        JsonNode pools = leer(get("/api/v1/procesos/{id}/pools", idDelProceso("Order fulfillment")));

        assertThat(pools).extracting(pool -> pool.get("tipoParticipante").asString())
                .containsExactly("EMPRESA", "CLIENTE", "SISTEMA_EXTERNO", "PROVEEDOR");
        long tiendaId = pools.get(0).get("id").asLong();
        assertThat(leer(get("/api/v1/pools/{id}/lanes", tiendaId)))
                .extracting(lane -> lane.get("nombre").asString())
                .containsExactly("Sales", "Warehouse");
        assertThat(leer(get("/api/v1/pools/{id}/arcos", tiendaId))).hasSize(5);
    }

    @Test
    @DisplayName("Todos los mensajes del proceso de pedidos se correlacionan por orderId")
    void DatosDemo_procesoDePedidos_correlacionaLosMensajesPorOrderId() throws Exception {
        JsonNode mensajes = leer(get("/api/v1/procesos/{id}/mensajes", idDelProceso("Order fulfillment")));

        assertThat(mensajes).hasSize(5);
        for (JsonNode mensaje : mensajes) {
            mockMvc.perform(conToken(get("/api/v1/mensajes/{id}/correlacion", mensaje.get("id").asLong())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.criterio").value("orderId"));
        }
    }

    private long idDelProceso(String nombre) throws Exception {
        for (JsonNode proceso : leer(get("/api/v1/procesos")).get("content")) {
            if (proceso.get("nombre").asString().equals(nombre)) {
                return proceso.get("id").asLong();
            }
        }
        throw new AssertionError("No existe el proceso demo " + nombre);
    }

    private JsonNode leer(MockHttpServletRequestBuilder peticion) throws Exception {
        String respuesta = mockMvc.perform(conToken(peticion))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return jsonMapper.readTree(respuesta);
    }

    private MockHttpServletRequestBuilder conToken(MockHttpServletRequestBuilder peticion) {
        return peticion.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }
}
