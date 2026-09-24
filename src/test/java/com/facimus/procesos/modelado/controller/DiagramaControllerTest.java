package com.facimus.procesos.modelado.controller;

import static com.facimus.procesos.security.ApiPrincipalRequestPostProcessor.principal;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.gestion.dto.response.ProcesoResponse;
import com.facimus.procesos.gestion.model.EstadoProceso;
import com.facimus.procesos.gestion.model.RolAcceso;
import com.facimus.procesos.modelado.dto.response.ActividadResponse;
import com.facimus.procesos.modelado.dto.response.ArcoResponse;
import com.facimus.procesos.modelado.dto.response.CorrelacionResponse;
import com.facimus.procesos.modelado.dto.response.DiagramaResponse;
import com.facimus.procesos.modelado.dto.response.EventoResponse;
import com.facimus.procesos.modelado.dto.response.GatewayResponse;
import com.facimus.procesos.modelado.dto.response.LaneResponse;
import com.facimus.procesos.modelado.dto.response.MensajeResponse;
import com.facimus.procesos.modelado.dto.response.PoolResponse;
import com.facimus.procesos.modelado.model.AccionSiFalla;
import com.facimus.procesos.modelado.model.Integracion;
import com.facimus.procesos.modelado.model.PoliticaSinCaso;
import com.facimus.procesos.modelado.model.TipoActividad;
import com.facimus.procesos.modelado.model.TipoEvento;
import com.facimus.procesos.modelado.model.TipoGateway;
import com.facimus.procesos.modelado.model.TipoParticipante;
import com.facimus.procesos.modelado.service.DiagramaService;

@WebMvcTest(DiagramaController.class)
class DiagramaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DiagramaService diagramaService;

    @Test
    @DisplayName("GET /api/v1/procesos/{id}/diagrama - el diagrama completo, tambien para solo lectura (200)")
    void obtener_diagramaCompleto() throws Exception {
        given(diagramaService.obtener(1L, 10L)).willReturn(diagrama());

        mockMvc.perform(get("/api/v1/procesos/10/diagrama").with(principal(RolAcceso.SOLO_LECTURA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.proceso.nombre").value("Order fulfillment"))
                .andExpect(jsonPath("$.compartido").value(false))
                .andExpect(jsonPath("$.pools[1].tipoParticipante").value("CLIENTE"))
                .andExpect(jsonPath("$.lanes[0].rolProcesoNombre").value("Sales"))
                .andExpect(jsonPath("$.actividades[0].laneId").value(3))
                .andExpect(jsonPath("$.gateways[0].tipoGateway").value("EXCLUSIVO"))
                .andExpect(jsonPath("$.arcos[0].destinoId").value(6))
                .andExpect(jsonPath("$.mensajes[0].poolOrigenId").value(2))
                .andExpect(jsonPath("$.correlaciones[0].criterio").value("orderId"));
    }

    @Test
    @DisplayName("GET /api/v1/procesos/{id}/diagrama - proceso que no es de la tienda (404)")
    void obtener_procesoAjeno() throws Exception {
        given(diagramaService.obtener(1L, 99L)).willThrow(new RecursoNoEncontradoException("Proceso no encontrado."));

        mockMvc.perform(get("/api/v1/procesos/99/diagrama").with(principal(RolAcceso.EDITOR)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Proceso no encontrado."));
    }

    @Test
    @DisplayName("GET /api/v1/procesos/{id}/diagrama - sin sesion retorna 401")
    void obtener_sinSesion() throws Exception {
        mockMvc.perform(get("/api/v1/procesos/10/diagrama"))
                .andExpect(status().isUnauthorized());
    }

    private static DiagramaResponse diagrama() {
        LocalDateTime creado = LocalDateTime.of(2026, 9, 21, 10, 0);
        return new DiagramaResponse(
                new ProcesoResponse(10L, "Order fulfillment", "Checkout to delivery", "Fulfillment",
                        EstadoProceso.PUBLICADO, true, creado, creado, 0L, null, null),
                false,
                List.of(new PoolResponse(1L, "Demo Store", TipoParticipante.EMPRESA, false,
                        Integracion.NINGUNA, 0, 10L, 0L, null, null, null, null),
                        new PoolResponse(2L, "Customer", TipoParticipante.CLIENTE, true,
                                Integracion.CLIENTE, 1, 10L, 0L, null, null, null, null)),
                List.of(new LaneResponse(3L, "Sales", 0, 1L, 4L, "Sales", 0L, null, null, null, null)),
                List.of(new ActividadResponse(5L, "Receive order", "Validate the cart", TipoActividad.USUARIO,
                        100, 80, 3L, 0L, null, null, null, null)),
                List.of(new GatewayResponse(6L, "Payment approved?", TipoGateway.EXCLUSIVO, 260, 80, 3L, 0L, null, null,
                        null, null)),
                List.of(new EventoResponse(11L, "Order received", TipoEvento.MENSAJE_INICIO, 20, 80, 3L, 0L,
                        null, null, null, null)),
                List.of(new ArcoResponse(7L, null, null, 5L, 6L, 1L, 0L, null, null, null, null)),
                List.of(new MensajeResponse(8L, "Order placed", "Cart items", 2L, 1L, null, 11L, null,
                        AccionSiFalla.CONTINUAR, null, false, List.of(), null, "order", null, 10L, 0L,
                        null, null, null, null)),
                List.of(new CorrelacionResponse(9L, "orderId", "orderId", PoliticaSinCaso.INICIAR_CASO,
                        8L, 0L, null, null, null, null)));
    }
}
