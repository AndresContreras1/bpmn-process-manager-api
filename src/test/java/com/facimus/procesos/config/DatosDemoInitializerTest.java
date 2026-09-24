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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.facimus.procesos.gestion.dto.request.LoginRequest;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Los datos demo vistos a traves de la API, como los ve quien entra a Swagger con la cuenta de demostracion.
 * Corre en dev, el unico perfil que siembra la tienda, pero sobre una H2 en memoria en vez de ./data.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:datos-demo;DB_CLOSE_DELAY=-1")
@ActiveProfiles("dev")
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
        assertThat(leer(get("/api/v1/pools/{id}/arcos", tiendaId))).hasSize(10);
    }

    @Test
    @DisplayName("Todos los mensajes del pedido se correlacionan por el campo orderId del cuerpo")
    void DatosDemo_procesoDePedidos_correlacionaLosMensajesPorOrderId() throws Exception {
        JsonNode mensajes = leer(get("/api/v1/procesos/{id}/mensajes", idDelProceso("Order fulfillment")));

        assertThat(mensajes).hasSize(6);
        for (JsonNode mensaje : mensajes) {
            mockMvc.perform(conToken(get("/api/v1/mensajes/{id}/correlacion", mensaje.get("id").asLong())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.criterio").value("orderId"))
                    .andExpect(jsonPath("$.campo").value("orderId"));
        }
        // Solo el pedido del cliente abre un caso; los demas se cuelgan de uno ya abierto.
        assertThat(mensajes).filteredOn(mensaje -> mensaje.get("nombre").asString().equals("Order placed"))
                .singleElement()
                .satisfies(pedido -> mockMvc.perform(conToken(get("/api/v1/mensajes/{id}/correlacion",
                                pedido.get("id").asLong())))
                        .andExpect(jsonPath("$.sinCaso").value("INICIAR_CASO")));
    }

    @Test
    @DisplayName("El diagrama del proceso de pedidos llega completo en una sola peticion")
    void DatosDemo_diagramaDePedidos_llegaCompletoEnUnaPeticion() throws Exception {
        JsonNode diagrama = leer(get("/api/v1/procesos/{id}/diagrama", idDelProceso("Order fulfillment")));

        assertThat(diagrama.get("proceso").get("estado").asString()).isEqualTo("PUBLICADO");
        assertThat(diagrama.get("pools")).extracting(pool -> pool.get("tipoParticipante").asString())
                .containsExactly("EMPRESA", "CLIENTE", "SISTEMA_EXTERNO", "PROVEEDOR");
        assertThat(diagrama.get("lanes")).extracting(lane -> lane.get("rolProcesoNombre").asString())
                .containsExactly("Sales", "Warehouse");
        assertThat(diagrama.get("actividades"))
                .extracting(actividad -> actividad.get("tipoActividad").asString())
                .containsExactly("USUARIO", "ENVIO", "SERVICIO", "USUARIO", "ENVIO");
        assertThat(diagrama.get("gateways")).hasSize(1);
        assertThat(diagrama.get("eventos"))
                .extracting(evento -> evento.get("tipoEvento").asString())
                .containsExactly("MENSAJE_INICIO", "MENSAJE_INTERMEDIO", "FIN", "MENSAJE_INTERMEDIO", "FIN");
        assertThat(diagrama.get("arcos")).hasSize(10);
        assertThat(diagrama.get("mensajes")).hasSize(6);
        // Cada mensaje sale de un nodo de la tienda o entra en uno; los de caja negra solo tienen un lado.
        assertThat(diagrama.get("mensajes"))
                .allMatch(mensaje -> !mensaje.get("nodoOrigenId").isNull()
                        || !mensaje.get("nodoDestinoId").isNull());
        assertThat(diagrama.get("mensajes"))
                .filteredOn(mensaje -> mensaje.get("nombre").asString().equals("Payment authorization request"))
                .singleElement()
                .satisfies(peticion -> {
                    assertThat(peticion.get("tipoDestino").asString()).isEqualTo("SERVICIO_WEB");
                    assertThat(peticion.get("siFalla").asString()).isEqualTo("MANEJAR_ERROR");
                    assertThat(peticion.get("variable").asString()).isEqualTo("paymentRequest");
                });
        assertThat(diagrama.get("correlaciones")).extracting(correlacion -> correlacion.get("campo").asString())
                .hasSize(6)
                .containsOnly("orderId");
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
