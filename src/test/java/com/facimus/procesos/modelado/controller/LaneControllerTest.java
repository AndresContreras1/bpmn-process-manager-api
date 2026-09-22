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
import com.facimus.procesos.modelado.dto.response.LaneResponse;
import com.facimus.procesos.modelado.service.LaneService;

import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

@WebMvcTest(LaneController.class)
class LaneControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LaneService laneService;

    @Test
    @DisplayName("GET /api/v1/pools/{poolId}/lanes - listar lanes (200)")
    void listar_lanes() throws Exception {
        LaneResponse lane = crearLane(1L, "Recepcion");
        given(laneService.listarPorPool(1L, 5L)).willReturn(List.of(lane));

        mockMvc.perform(get("/api/v1/pools/5/lanes").with(principal(RolAcceso.EDITOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombre").value("Recepcion"));
    }

    @Test
    @DisplayName("GET /api/v1/lanes/{id} - detalle lane (200)")
    void detalle_lane() throws Exception {
        LaneResponse lane = crearLane(1L, "Recepcion");
        given(laneService.obtener(1L, 1L)).willReturn(lane);

        mockMvc.perform(get("/api/v1/lanes/1").with(principal(RolAcceso.EDITOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Recepcion"));
    }

    @Test
    @DisplayName("GET /api/v1/lanes/{id} - sin sesion retorna 401")
    void detalle_sin_sesion() throws Exception {
        mockMvc.perform(get("/api/v1/lanes/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/v1/pools/{poolId}/lanes - crear lane (201)")
    void crear_lane() throws Exception {
        LaneResponse lane = crearLane(2L, "Analisis");
        given(laneService.crear(eq(1L), eq(5L), anyString(), anyLong())).willReturn(lane);

        mockMvc.perform(post("/api/v1/pools/5/lanes")
                        .with(principal(RolAcceso.EDITOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Analisis","rolProcesoId":1}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/lanes/2"))
                .andExpect(jsonPath("$.nombre").value("Analisis"));
    }

    @Test
    @DisplayName("POST /api/v1/pools/{poolId}/lanes - validacion falla (400)")
    void crear_validacion_falla() throws Exception {
        mockMvc.perform(post("/api/v1/pools/5/lanes")
                        .with(principal(RolAcceso.EDITOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"","rolProcesoId":null}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT /api/v1/lanes/{id} - editar lane (200)")
    void editar_lane() throws Exception {
        LaneResponse lane = crearLane(1L, "Recepcion v2");
        given(laneService.editar(eq(1L), eq(1L), anyString(), anyLong(), eq(3L))).willReturn(lane);

        mockMvc.perform(put("/api/v1/lanes/1")
                        .with(principal(RolAcceso.EDITOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Recepcion v2","rolProcesoId":1,"version":3}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Recepcion v2"));
    }

    @Test
    @DisplayName("DELETE /api/v1/lanes/{id} - eliminar lane (204)")
    void eliminar_lane() throws Exception {
        doNothing().when(laneService).eliminar(1L, 1L);

        mockMvc.perform(delete("/api/v1/lanes/1").with(principal(RolAcceso.ADMINISTRADOR)))
                .andExpect(status().isNoContent());
    }

    private LaneResponse crearLane(Long id, String nombre) {
        return new LaneResponse(id, nombre, 0, 5L, 1L, "Analista", 0L);
    }

}
