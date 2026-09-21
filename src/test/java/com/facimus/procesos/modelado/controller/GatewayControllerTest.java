package com.facimus.procesos.modelado.controller;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static com.facimus.procesos.security.ApiPrincipalRequestPostProcessor.principal;
import com.facimus.procesos.gestion.model.RolAcceso;
import com.facimus.procesos.modelado.model.Gateway;
import com.facimus.procesos.modelado.model.Lane;
import com.facimus.procesos.modelado.model.TipoGateway;
import com.facimus.procesos.modelado.service.GatewayService;

import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

@WebMvcTest(GatewayController.class)
class GatewayControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GatewayService gatewayService;

    @Test
    @DisplayName("POST /api/v1/lanes/{laneId}/gateways - crear gateway (201)")
    void crear_gateway() throws Exception {
        Gateway gw = crearGateway(1L, "Decision pago");
        given(gatewayService.crear(eq(1L), eq(3L), anyString(), any(), anyInt(), anyInt())).willReturn(gw);

        mockMvc.perform(post("/api/v1/lanes/3/gateways")
                        .with(principal(RolAcceso.EDITOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Decision pago","tipoGateway":"EXCLUSIVO","posicionX":300,"posicionY":150}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/gateways/1"))
                .andExpect(jsonPath("$.nombre").value("Decision pago"))
                .andExpect(jsonPath("$.tipoGateway").value("EXCLUSIVO"));
    }

    @Test
    @DisplayName("POST /api/v1/lanes/{laneId}/gateways - validacion falla (400)")
    void crear_validacion_falla() throws Exception {
        mockMvc.perform(post("/api/v1/lanes/3/gateways")
                        .with(principal(RolAcceso.EDITOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"","tipoGateway":null,"posicionX":0,"posicionY":0}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/v1/gateways/{id} - detalle gateway (200)")
    void detalle_gateway() throws Exception {
        Gateway gw = crearGateway(1L, "Decision pago");
        given(gatewayService.obtener(1L, 1L)).willReturn(gw);

        mockMvc.perform(get("/api/v1/gateways/1").with(principal(RolAcceso.EDITOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Decision pago"));
    }

    @Test
    @DisplayName("GET /api/v1/gateways/{id} - sin sesion retorna 401")
    void detalle_sin_sesion() throws Exception {
        mockMvc.perform(get("/api/v1/gateways/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/v1/lanes/{laneId}/gateways - listar gateways (200)")
    void listar_gateways() throws Exception {
        Gateway gw = crearGateway(1L, "Decision pago");
        given(gatewayService.listarPorLane(1L, 3L)).willReturn(List.of(gw));

        mockMvc.perform(get("/api/v1/lanes/3/gateways").with(principal(RolAcceso.EDITOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombre").value("Decision pago"));
    }

    @Test
    @DisplayName("GET /api/v1/lanes/{laneId}/gateways - sin sesion retorna 401")
    void listar_sin_sesion() throws Exception {
        mockMvc.perform(get("/api/v1/lanes/3/gateways"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("PUT /api/v1/gateways/{id} - editar gateway (200)")
    void editar_gateway() throws Exception {
        Gateway gw = crearGateway(1L, "Decision envio");
        gw.setTipoGateway(TipoGateway.PARALELO);
        given(gatewayService.editar(eq(1L), eq(1L), anyString(), any(), anyInt(), anyInt())).willReturn(gw);

        mockMvc.perform(put("/api/v1/gateways/1")
                        .with(principal(RolAcceso.EDITOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Decision envio","tipoGateway":"PARALELO","posicionX":300,"posicionY":150}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipoGateway").value("PARALELO"));
    }

    @Test
    @DisplayName("DELETE /api/v1/gateways/{id} - eliminar gateway (204)")
    void eliminar_gateway() throws Exception {
        doNothing().when(gatewayService).eliminar(1L, 1L);
        mockMvc.perform(delete("/api/v1/gateways/1").with(principal(RolAcceso.ADMINISTRADOR)))
                .andExpect(status().isNoContent());
    }

    private Gateway crearGateway(Long id, String nombre) {
        Lane lane = new Lane();
        lane.setId(3L);

        Gateway gw = new Gateway();
        gw.setId(id);
        gw.setNombre(nombre);
        gw.setTipoGateway(TipoGateway.EXCLUSIVO);
        gw.setPosicionX(300);
        gw.setPosicionY(150);
        gw.setLane(lane);
        return gw;
    }

}
