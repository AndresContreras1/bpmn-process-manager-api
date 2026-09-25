package com.facimus.procesos.ejecucion.controller;

import static com.facimus.procesos.security.ApiPrincipalRequestPostProcessor.principal;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.model.RolAcceso;
import com.facimus.procesos.ejecucion.dto.response.PanelDeSimulacionResponse;
import com.facimus.procesos.ejecucion.dto.response.PedidosSimuladosResponse;
import com.facimus.procesos.ejecucion.dto.response.PendientesPorSocioResponse;
import com.facimus.procesos.ejecucion.service.SimulacionService;
import com.facimus.procesos.gestion.model.ModoSimulacion;
import com.facimus.procesos.modelado.model.Integracion;

@WebMvcTest(SimulacionController.class)
class SimulacionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SimulacionService simulacionService;

    @Test
    @DisplayName("GET /api/v1/simulacion - responde el reloj, el modo y lo que queda pendiente (200)")
    void panel_respondeDondeEstaLaSimulacion() throws Exception {
        given(simulacionService.panel(1L)).willReturn(panel(7));

        mockMvc.perform(get("/api/v1/simulacion").with(principal(RolAcceso.ADMINISTRADOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reloj").value(7))
                .andExpect(jsonPath("$.modo").value("MANUAL"))
                .andExpect(jsonPath("$.salientesPendientes").value(3))
                .andExpect(jsonPath("$.porSocio[0].socio").value("PAGOS"))
                .andExpect(jsonPath("$.porSocio[0].cantidad").value(3))
                .andExpect(jsonPath("$.entrantesEnEspera").value(1));
    }

    @Test
    @DisplayName("POST /api/v1/simulacion/tick - mueve el reloj y responde donde quedo (200)")
    void tick_mueveElRelojYRespondeElPanel() throws Exception {
        given(simulacionService.tick(1L, 3)).willReturn(panel(10));

        mockMvc.perform(post("/api/v1/simulacion/tick")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ticks\":3}")
                        .with(principal(RolAcceso.ADMINISTRADOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reloj").value(10));

        verify(simulacionService).tick(1L, 3);
    }

    @Test
    @DisplayName("POST /api/v1/simulacion/tick - cien ticks es el tope, y ciento uno no llega al service (400)")
    void tick_porEncimaDelTope_esInvalido() throws Exception {
        mockMvc.perform(post("/api/v1/simulacion/tick")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ticks\":101}")
                        .with(principal(RolAcceso.ADMINISTRADOR)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.ticks").exists());

        verifyNoInteractions(simulacionService);
    }

    @Test
    @DisplayName("POST /api/v1/simulacion/tick - sin ticks no llega al service (400)")
    void tick_sinTicks_esInvalido() throws Exception {
        mockMvc.perform(post("/api/v1/simulacion/tick")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .with(principal(RolAcceso.ADMINISTRADOR)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(simulacionService);
    }

    @Test
    @DisplayName("POST /api/v1/simulacion/tick - la regla del service sale como conflicto (409)")
    void tick_reglaDelService_esConflicto() throws Exception {
        given(simulacionService.tick(1L, 1))
                .willThrow(new ReglaNegocioException("El reloj se mueve entre 1 y 100 ticks por vez."));

        mockMvc.perform(post("/api/v1/simulacion/tick")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ticks\":1}")
                        .with(principal(RolAcceso.ADMINISTRADOR)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("POST /api/v1/simulacion/pedidos - pide la tanda y responde que hizo el proceso con ella (200)")
    void pedidos_respondeLoQueElProcesoHizo() throws Exception {
        given(simulacionService.pedidos(1L, 10L, 20, Map.of("channel", "web")))
                .willReturn(new PedidosSimuladosResponse("Order placed", 20, 20, List.of("SIM-10-1")));

        mockMvc.perform(post("/api/v1/simulacion/pedidos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"procesoId\":10,\"cantidad\":20,\"plantilla\":{\"channel\":\"web\"}}")
                        .with(principal(RolAcceso.ADMINISTRADOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value("Order placed"))
                .andExpect(jsonPath("$.pedidos").value(20))
                .andExpect(jsonPath("$.casosNuevos").value(20))
                .andExpect(jsonPath("$.referencias[0]").value("SIM-10-1"));
    }

    @Test
    @DisplayName("POST /api/v1/simulacion/pedidos - doscientos uno no llega al service (400)")
    void pedidos_porEncimaDelTope_esInvalido() throws Exception {
        mockMvc.perform(post("/api/v1/simulacion/pedidos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"procesoId\":10,\"cantidad\":201}")
                        .with(principal(RolAcceso.ADMINISTRADOR)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.cantidad").exists());

        verifyNoInteractions(simulacionService);
    }

    @Test
    @DisplayName("POST /api/v1/simulacion/pedidos - sin proceso no llega al service (400)")
    void pedidos_sinProceso_esInvalido() throws Exception {
        mockMvc.perform(post("/api/v1/simulacion/pedidos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cantidad\":5}")
                        .with(principal(RolAcceso.ADMINISTRADOR)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.procesoId").exists());

        verifyNoInteractions(simulacionService);
    }

    private static PanelDeSimulacionResponse panel(int reloj) {
        return new PanelDeSimulacionResponse(reloj, ModoSimulacion.MANUAL, 3,
                List.of(new PendientesPorSocioResponse(Integracion.PAGOS, 3)), 1);
    }
}
